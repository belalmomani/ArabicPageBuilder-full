package com.arabicpagebuilder.core.session

import com.arabicpagebuilder.core.edit.EditError
import com.arabicpagebuilder.core.edit.EditResult
import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.IdGenerator
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.find
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorSessionTest {

    private class SeqIds : IdGenerator {
        private var n = 0
        override fun next(): String = "e${++n}"
    }

    private fun newSession(): EditorSession {
        val ids = SeqIds()
        val doc = PageDocument.empty("ع", ids = ids) // الجذر e1
        return EditorSession(doc, ids)
    }

    private fun EditorSession.childIds(id: String): List<String> = document.find(id)!!.children.map { it.id }
    private fun EditResult.error(): EditError = assertIs<EditResult.Failure>(this).error
    private fun EditResult.assertOk() = assertIs<EditResult.Success>(this)

    @Test
    fun addWithoutSelectionGoesToRootAndSelectsNewElement() {
        val s = newSession()
        s.addElement(ElementType.HEADING).assertOk()
        assertEquals(listOf("e2"), s.childIds("e1"))
        assertEquals("e2", s.selectedId)
        assertEquals("عنوان رئيسي", s.selected?.props?.get("text"))
    }

    @Test
    fun addWhileContainerSelectedNestsInsideIt() {
        val s = newSession()
        s.addElement(ElementType.CARD).assertOk()   // e2 محدد
        s.addElement(ElementType.PARAGRAPH).assertOk()
        assertEquals(listOf("e3"), s.childIds("e2"))
        assertEquals("e3", s.selectedId)
    }

    @Test
    fun addWhileLeafSelectedInsertsRightAfterIt() {
        val s = newSession()
        s.addElement(ElementType.HEADING).assertOk()    // e2
        s.addElement(ElementType.PARAGRAPH).assertOk()  // e3 بعد e2 لأن e2 ورقي
        s.select("e2")
        s.addElement(ElementType.BUTTON).assertOk()     // e4 بين e2 وe3
        assertEquals(listOf("e2", "e4", "e3"), s.childIds("e1"))
    }

    @Test
    fun cannotAddRootType() {
        assertEquals(EditError.CANNOT_ADD_ROOT_TYPE, newSession().addElement(ElementType.PAGE_ROOT).error())
    }

    @Test
    fun selectValidatesExistence() {
        val s = newSession()
        assertFalse(s.select("ghost"))
        assertNull(s.selectedId)
        assertTrue(s.select("e1"))
        assertTrue(s.select(null))
    }

    @Test
    fun setPropertyChangesSelectedAndBumpsRevisionOnlyOnRealChange() {
        val s = newSession()
        s.addElement(ElementType.HEADING).assertOk()
        val rev = s.revision
        s.setProperty("text", "جديد").assertOk()
        assertEquals("جديد", s.selected?.props?.get("text"))
        assertEquals(rev + 1, s.revision)
        s.setProperty("text", "جديد").assertOk() // نفس القيمة: لا تغيير
        assertEquals(rev + 1, s.revision)
        s.setProperty("text", "  ").assertOk()     // فارغ = حذف الخاصية
        assertNull(s.selected?.props?.get("text"))
    }

    @Test
    fun setPropertyWithoutSelectionFails() {
        assertEquals(EditError.ELEMENT_NOT_FOUND, newSession().setProperty("text", "x").error())
    }

    @Test
    fun removeSelectsParentAndRootCannotBeRemoved() {
        val s = newSession()
        s.addElement(ElementType.CARD).assertOk()       // e2
        s.addElement(ElementType.PARAGRAPH).assertOk()  // e3 داخل e2
        s.removeSelected().assertOk()
        assertEquals("e2", s.selectedId)
        assertTrue(s.childIds("e2").isEmpty())
        s.select("e1")
        assertEquals(EditError.CANNOT_MODIFY_ROOT, s.removeSelected().error())
    }

    @Test
    fun duplicateSelectsTheCopyAndKeepsUniqueIds() {
        val s = newSession()
        s.addElement(ElementType.CARD).assertOk()       // e2
        s.addElement(ElementType.HEADING).assertOk()    // e3 داخل e2
        s.select("e2")
        s.duplicateSelected().assertOk()
        val rootChildren = s.childIds("e1")
        assertEquals(2, rootChildren.size)
        assertEquals(rootChildren[1], s.selectedId)
        assertTrue(s.selectedId != "e2")
        val all = TreeRows.of(s.document).map { it.element.id }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun moveUpDownKeepSelectionAndReportEdges() {
        val s = newSession()
        repeat(3) { s.select("e1"); s.addElement(ElementType.PARAGRAPH).assertOk() } // e2,e3,e4
        s.select("e4")
        s.moveSelectedUp().assertOk()
        assertEquals(listOf("e2", "e4", "e3"), s.childIds("e1"))
        assertEquals("e4", s.selectedId)
        s.select("e2")
        assertEquals(EditError.ALREADY_FIRST, s.moveSelectedUp().error())
        s.select("e3")
        assertEquals(EditError.ALREADY_LAST, s.moveSelectedDown().error())
    }

    @Test
    fun moveToAnotherContainerAndTargetsExcludeSelfDescendantsAndParent() {
        val s = newSession()
        s.addElement(ElementType.SECTION).assertOk()    // e2
        s.select("e1"); s.addElement(ElementType.CARD).assertOk()   // e3
        s.addElement(ElementType.CONTAINER).assertOk()  // e4 داخل e3
        s.select("e1"); s.addElement(ElementType.PARAGRAPH).assertOk() // e5
        s.select("e3")
        val targets = s.validMoveTargets().map { it.element.id }
        assertEquals(listOf("e2"), targets.filter { it != "e1" }) // لا e3 ولا e4 (أحفاده)، والأب e1 مستبعد
        assertFalse("e1" in targets)
        s.moveSelectedTo("e2").assertOk()
        assertEquals(listOf("e3"), s.childIds("e2"))
        assertEquals("e3", s.selectedId)
        // الجذر صار هدفًا صالحًا الآن لأنه ليس أبًا
        s.select("e5")
        assertTrue(s.validMoveTargets().any { it.element.id == "e2" })
    }

    @Test
    fun movingIntoOwnDescendantIsRejectedWithMessageCode() {
        val s = newSession()
        s.addElement(ElementType.CARD).assertOk()       // e2
        s.addElement(ElementType.CONTAINER).assertOk()  // e3 داخل e2
        s.select("e2")
        assertEquals(EditError.CANNOT_MOVE_INTO_DESCENDANT, s.moveSelectedTo("e3").error())
    }

    @Test
    fun undoRedoRestoreDocumentAndFixSelection() {
        val s = newSession()
        s.addElement(ElementType.HEADING).assertOk()    // e2 محدد
        assertTrue(s.canUndo)
        assertTrue(s.undo())
        assertTrue(s.childIds("e1").isEmpty())
        assertNull(s.selectedId)                          // e2 لم يعد موجودًا
        assertTrue(s.canRedo)
        assertTrue(s.redo())
        assertEquals(listOf("e2"), s.childIds("e1"))
        assertFalse(s.redo())
        s.undo(); s.undo()
        assertFalse(s.undo())
    }

    @Test
    fun failedOperationDoesNotChangeDocumentOrRevision() {
        val s = newSession()
        val doc = s.document
        val rev = s.revision
        s.select("e1")
        s.removeSelected()
        s.moveSelectedUp()
        assertEquals(doc, s.document)
        assertEquals(rev, s.revision)
        assertFalse(s.canUndo)
    }

    @Test
    fun treeRowsAreFlattenedWithDepth() {
        val doc = PageDocument.empty("ع", ids = IdGenerator { "r" }).let {
            it.copy(root = it.root.copy(children = listOf(Element("a", ElementType.CARD, emptyMap(), listOf(Element("b", ElementType.HEADING))))))
        }
        assertEquals(listOf("r" to 0, "a" to 1, "b" to 2), TreeRows.of(doc).map { it.element.id to it.depth })
    }
}

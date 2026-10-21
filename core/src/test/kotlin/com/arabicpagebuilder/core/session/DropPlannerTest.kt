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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DropPlannerTest {

    /** r: [a(card): [a1, a2], b(paragraph), c(section): []] */
    private fun sample(): PageDocument {
        val a = Element("a", ElementType.CARD, emptyMap(), listOf(Element("a1", ElementType.PARAGRAPH), Element("a2", ElementType.PARAGRAPH)))
        val root = Element("r", ElementType.PAGE_ROOT, emptyMap(), listOf(a, Element("b", ElementType.PARAGRAPH), Element("c", ElementType.SECTION)))
        return PageDocument("t", com.arabicpagebuilder.core.model.TextDirection.RTL, root)
    }

    private fun plan(dragged: String?, target: String, zone: DropZone) = DropPlanner.plan(sample(), dragged, target, zone)

    @Test
    fun newElementPlacement() {
        assertEquals(DropPlan("r", 1), plan(null, "b", DropZone.BEFORE))
        assertEquals(DropPlan("r", 2), plan(null, "b", DropZone.AFTER))
        assertEquals(DropPlan("r", 2), plan(null, "b", DropZone.INSIDE)) // ورقي: المنتصف = بعده
        assertEquals(DropPlan("a", 2), plan(null, "a", DropZone.INSIDE))
        assertEquals(DropPlan("a", 1), plan(null, "a2", DropZone.BEFORE))
        assertEquals(DropPlan("r", 3), plan(null, "r", DropZone.BEFORE)) // الجذر دائمًا بداخله في النهاية
        assertEquals(DropPlan("r", 3), plan(null, "r", DropZone.AFTER))
    }

    @Test
    fun movePlacementAccountsForRemovalShift() {
        assertEquals(DropPlan("r", 0), plan("b", "a", DropZone.BEFORE))
        assertEquals(DropPlan("r", 2), plan("a", "c", DropZone.AFTER))   // بعد إزالة a: [b,c] فالنهاية = 2
        assertEquals(DropPlan("a", 1), plan("a1", "a2", DropZone.AFTER)) // [a2] ثم a1 بعده
        assertEquals(DropPlan("a", 0), plan("a2", "a1", DropZone.BEFORE))
        assertEquals(DropPlan("c", 0), plan("b", "c", DropZone.INSIDE))
        assertEquals(DropPlan("a", 1), plan("a1", "a", DropZone.INSIDE)) // نهاية قائمة أبناء a بعد إزالة a1
    }

    @Test
    fun invalidDropsReturnNull() {
        assertNull(plan("a", "a", DropZone.BEFORE))       // على نفسه
        assertNull(plan("a", "a1", DropZone.INSIDE))      // على أحد أحفاده
        assertNull(plan("a", "a2", DropZone.AFTER))
        assertNull(plan("r", "b", DropZone.BEFORE))       // لا يُنقل الجذر
        assertNull(plan("b", "ghost", DropZone.BEFORE))
        assertNull(plan("ghost", "b", DropZone.BEFORE))
        assertNull(plan(null, "ghost", DropZone.INSIDE))
    }

    // ---- عبر الجلسة ----

    private class SeqIds : IdGenerator {
        private var n = 0
        override fun next(): String = "n${++n}"
    }

    private fun session() = EditorSession(sample(), SeqIds())
    private fun EditorSession.ids(id: String) = document.find(id)!!.children.map { it.id }

    @Test
    fun dragMoveReordersAndNests() {
        val s = session()
        assertIs<EditResult.Success>(s.dragMove("b", "a", DropZone.BEFORE))
        assertEquals(listOf("b", "a", "c"), s.ids("r"))
        assertEquals("b", s.selectedId)
        assertIs<EditResult.Success>(s.dragMove("b", "c", DropZone.INSIDE))
        assertEquals(listOf("a", "c"), s.ids("r"))
        assertEquals(listOf("b"), s.ids("c"))
        assertTrue(s.undo())
        assertEquals(listOf("b", "a", "c"), s.ids("r"))
    }

    @Test
    fun dragIntoOwnDescendantFailsWithClearError() {
        val s = session()
        val r = s.dragMove("a", "a1", DropZone.INSIDE)
        assertEquals(EditError.CANNOT_MOVE_INTO_DESCENDANT, assertIs<EditResult.Failure>(r).error)
        assertEquals(EditError.CANNOT_MOVE_INTO_SELF, assertIs<EditResult.Failure>(s.dragMove("a", "a", DropZone.BEFORE)).error)
        assertEquals(EditError.CANNOT_MODIFY_ROOT, assertIs<EditResult.Failure>(s.dragMove("r", "b", DropZone.BEFORE)).error)
        assertEquals(listOf("a", "b", "c"), s.ids("r"))
        assertTrue(!s.canUndo)
    }

    @Test
    fun dropNewElementAndPresetAtPosition() {
        val s = session()
        assertIs<EditResult.Success>(s.dropNewElement(ElementType.HEADING, "b", DropZone.BEFORE))
        assertEquals(ElementType.HEADING, s.document.find(s.ids("r")[1])!!.type)
        assertEquals(s.ids("r")[1], s.selectedId)
        assertIs<EditResult.Success>(s.dropPreset(ElementCatalog.PRESET_CONTACT_FORM, "c", DropZone.INSIDE))
        val form = s.document.find(s.ids("c").single())!!
        assertEquals(ElementType.FORM, form.type)
        assertEquals(4, form.children.size)
        assertEquals(EditError.PARENT_NOT_FOUND, assertIs<EditResult.Failure>(s.dropNewElement(ElementType.TEXT, "ghost", DropZone.INSIDE)).error)
        assertEquals(EditError.ELEMENT_NOT_FOUND, assertIs<EditResult.Failure>(s.dropPreset("nope", "b", DropZone.AFTER)).error)
    }

    @Test
    fun addPresetFollowsSelectionRules() {
        val s = session()
        s.select("c")
        assertIs<EditResult.Success>(s.addPreset(ElementCatalog.PRESET_CONTACT_FORM))
        assertEquals(1, s.ids("c").size)
        assertEquals(s.ids("c").single(), s.selectedId)
        assertEquals(EditError.ELEMENT_NOT_FOUND, assertIs<EditResult.Failure>(s.addPreset("nope")).error)
    }
}

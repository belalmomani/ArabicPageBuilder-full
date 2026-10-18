package com.arabicpagebuilder.core.edit

import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.IdGenerator
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.TextDirection
import com.arabicpagebuilder.core.model.find
import com.arabicpagebuilder.core.model.subtree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentEditorTest {

    private class SeqIds(private var n: Int = 0) : IdGenerator {
        override fun next(): String = "e${++n}"
    }

    // الجذر دائمًا "e1" لأن أول معرّف يُولَّد له.
    private fun newDoc(): PageDocument = PageDocument.empty("اختبار", ids = SeqIds())

    private fun el(id: String, type: ElementType, vararg children: Element, props: Map<String, String> = emptyMap()) =
        Element(id, type, props, children.toList())

    private fun EditResult.doc(): PageDocument = assertIs<EditResult.Success>(this).document
    private fun EditResult.error(): EditError = assertIs<EditResult.Failure>(this).error
    private fun Element.childIds(): List<String> = children.map { it.id }

    private fun docWithParagraphs(): PageDocument {
        var doc = newDoc()
        for (id in listOf("a", "b", "c")) {
            doc = DocumentEditor.add(doc, "e1", el(id, ElementType.PARAGRAPH)).doc()
        }
        return doc
    }

    @Test
    fun newDocumentHasEmptyRootAndRtlDefault() {
        val doc = newDoc()
        assertEquals(ElementType.PAGE_ROOT, doc.root.type)
        assertTrue(doc.root.children.isEmpty())
        assertEquals(TextDirection.RTL, doc.direction)
        assertEquals("اختبار", doc.title)
    }

    @Test
    fun addHeadingThenChangeItsText() {
        var doc = DocumentEditor.add(newDoc(), "e1", el("h", ElementType.HEADING, props = mapOf("text" to "عنوان"))).doc()
        doc = DocumentEditor.setProperty(doc, "h", "text", "عنوان جديد").doc()
        assertEquals("عنوان جديد", doc.find("h")?.props?.get("text"))
    }

    @Test
    fun setPropertyNullRemovesAndBlankKeyFails() {
        var doc = DocumentEditor.add(newDoc(), "e1", el("h", ElementType.HEADING, props = mapOf("text" to "x"))).doc()
        doc = DocumentEditor.setProperty(doc, "h", "text", null).doc()
        assertNull(doc.find("h")?.props?.get("text"))
        assertEquals(EditError.INVALID_PROPERTY_KEY, DocumentEditor.setProperty(doc, "h", " ", "v").error())
        assertEquals(EditError.ELEMENT_NOT_FOUND, DocumentEditor.setProperty(doc, "nope", "k", "v").error())
    }

    @Test
    fun nestedStructureIsSupported() {
        val container = el("box", ElementType.CONTAINER, el("p", ElementType.PARAGRAPH), el("btn", ElementType.BUTTON))
        val card = el("card", ElementType.CARD, el("img", ElementType.IMAGE))
        var doc = DocumentEditor.add(newDoc(), "e1", container).doc()
        doc = DocumentEditor.add(doc, "box", card).doc()
        assertEquals(listOf("p", "btn", "card"), doc.find("box")?.childIds())
        assertEquals(listOf("img"), doc.find("card")?.childIds())
    }

    @Test
    fun cannotAddChildrenUnderLeafElements() {
        val doc = DocumentEditor.add(newDoc(), "e1", el("p", ElementType.PARAGRAPH)).doc()
        assertEquals(
            EditError.PARENT_CANNOT_HAVE_CHILDREN,
            DocumentEditor.add(doc, "p", el("x", ElementType.BUTTON)).error(),
        )
        // عنصر ورقي لا يجوز أن يحمل أبناء أصلًا
        val bad = el("img", ElementType.IMAGE, el("x", ElementType.BUTTON))
        assertEquals(EditError.PARENT_CANNOT_HAVE_CHILDREN, DocumentEditor.add(newDoc(), "e1", bad).error())
    }

    @Test
    fun rejectsDuplicateIdsAndRootTypeAndBadIndex() {
        val doc = DocumentEditor.add(newDoc(), "e1", el("a", ElementType.PARAGRAPH)).doc()
        assertEquals(EditError.DUPLICATE_ID, DocumentEditor.add(doc, "e1", el("a", ElementType.PARAGRAPH)).error())
        assertEquals(EditError.DUPLICATE_ID, DocumentEditor.add(doc, "e1", el("e1", ElementType.PARAGRAPH)).error())
        assertEquals(EditError.CANNOT_ADD_ROOT_TYPE, DocumentEditor.add(doc, "e1", el("r", ElementType.PAGE_ROOT)).error())
        assertEquals(EditError.INDEX_OUT_OF_RANGE, DocumentEditor.add(doc, "e1", el("z", ElementType.PARAGRAPH), 5).error())
        assertEquals(EditError.PARENT_NOT_FOUND, DocumentEditor.add(doc, "ghost", el("z", ElementType.PARAGRAPH)).error())
    }

    @Test
    fun reorderWithMoveUpAndMoveDown() {
        var doc = docWithParagraphs()
        doc = DocumentEditor.moveUp(doc, "c").doc()
        assertEquals(listOf("a", "c", "b"), doc.root.childIds())
        doc = DocumentEditor.moveDown(doc, "a").doc()
        assertEquals(listOf("c", "a", "b"), doc.root.childIds())
        assertEquals(EditError.ALREADY_FIRST, DocumentEditor.moveUp(doc, "c").error())
        assertEquals(EditError.ALREADY_LAST, DocumentEditor.moveDown(doc, "b").error())
    }

    @Test
    fun moveToAnotherContainerAtGivenIndex() {
        var doc = docWithParagraphs()
        doc = DocumentEditor.add(doc, "e1", el("box", ElementType.CONTAINER, el("x", ElementType.PARAGRAPH))).doc()
        doc = DocumentEditor.move(doc, "a", "box", 0).doc()
        assertEquals(listOf("b", "c", "box"), doc.root.childIds())
        assertEquals(listOf("a", "x"), doc.find("box")?.childIds())
        // نقل داخل نفس الأب: الموضع بعد إزالة العنصر
        doc = DocumentEditor.move(doc, "b", "e1", 1).doc()
        assertEquals(listOf("c", "b", "box"), doc.root.childIds())
    }

    @Test
    fun cannotMoveIntoSelfOrDescendant() {
        var doc = DocumentEditor.add(newDoc(), "e1", el("outer", ElementType.CONTAINER, el("inner", ElementType.CONTAINER))).doc()
        assertEquals(EditError.CANNOT_MOVE_INTO_SELF, DocumentEditor.move(doc, "outer", "outer").error())
        assertEquals(EditError.CANNOT_MOVE_INTO_DESCENDANT, DocumentEditor.move(doc, "outer", "inner").error())
        assertEquals(EditError.CANNOT_MODIFY_ROOT, DocumentEditor.move(doc, "e1", "outer").error())
        // الفشل لا يغيّر المستند الأصلي
        assertEquals(listOf("outer"), doc.root.childIds())
    }

    @Test
    fun cannotMoveIntoLeafElement() {
        val doc = docWithParagraphs()
        assertEquals(EditError.PARENT_CANNOT_HAVE_CHILDREN, DocumentEditor.move(doc, "a", "b").error())
    }

    @Test
    fun removeDeletesSubtreeButNotRoot() {
        var doc = DocumentEditor.add(newDoc(), "e1", el("box", ElementType.CONTAINER, el("p", ElementType.PARAGRAPH))).doc()
        assertEquals(EditError.CANNOT_MODIFY_ROOT, DocumentEditor.remove(doc, "e1").error())
        assertEquals(EditError.ELEMENT_NOT_FOUND, DocumentEditor.remove(doc, "ghost").error())
        doc = DocumentEditor.remove(doc, "box").doc()
        assertNull(doc.find("box"))
        assertNull(doc.find("p"))
    }

    @Test
    fun duplicateCopiesSubtreeWithFreshUniqueIds() {
        val card = el("card", ElementType.CARD, el("t", ElementType.HEADING, props = mapOf("text" to "س")))
        var doc = DocumentEditor.add(newDoc(), "e1", card).doc()
        doc = DocumentEditor.add(doc, "e1", el("after", ElementType.PARAGRAPH)).doc()
        doc = DocumentEditor.duplicate(doc, "card", SeqIds(100)).doc()
        assertEquals(3, doc.root.children.size)
        assertEquals("card", doc.root.children[0].id)
        assertEquals("after", doc.root.children[2].id)
        val copy = doc.root.children[1]
        assertEquals(ElementType.CARD, copy.type)
        assertEquals("س", copy.children.single().props["text"])
        val ids = doc.root.subtree().map { it.id }.toList()
        assertEquals(ids.size, ids.toSet().size)
        assertNotNull(doc.find(copy.id))
        assertEquals(EditError.CANNOT_MODIFY_ROOT, DocumentEditor.duplicate(doc, "e1", SeqIds(200)).error())
    }

    @Test
    fun operationsNeverMutateTheOriginalDocument() {
        val original = docWithParagraphs()
        DocumentEditor.remove(original, "a")
        DocumentEditor.moveUp(original, "c")
        DocumentEditor.setProperty(original, "a", "k", "v")
        assertEquals(listOf("a", "b", "c"), original.root.childIds())
        assertTrue(original.find("a")?.props?.isEmpty() == true)
    }
}

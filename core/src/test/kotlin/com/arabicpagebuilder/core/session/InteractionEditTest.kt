package com.arabicpagebuilder.core.session

import com.arabicpagebuilder.core.edit.DocumentEditor
import com.arabicpagebuilder.core.edit.EditError
import com.arabicpagebuilder.core.edit.EditResult
import com.arabicpagebuilder.core.model.Action
import com.arabicpagebuilder.core.model.ActionType
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.IdGenerator
import com.arabicpagebuilder.core.model.Interaction
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.find
import com.arabicpagebuilder.core.model.Trigger
import com.arabicpagebuilder.core.model.TriggerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InteractionEditTest {

    private class SeqIds : IdGenerator {
        private var n = 0
        override fun next(): String = "e${++n}"
    }

    private fun session(): EditorSession {
        val ids = SeqIds()
        return EditorSession(PageDocument.empty("ع", ids = ids), ids)
    }

    private fun rule(id: String, name: String = "", value: String = "") =
        Interaction(id, name, Trigger(TriggerType.PAGE_LOAD), true, emptyList(), listOf(Action(ActionType.MESSAGE, "", value.ifEmpty { "مرحبا" })))

    private fun EditResult.error() = assertIs<EditResult.Failure>(this).error

    @Test
    fun addUpdateRemoveInteraction() {
        val s = session()
        assertIs<EditResult.Success>(s.addInteraction(rule("r1", "الأول")))
        assertIs<EditResult.Success>(s.addInteraction(rule("r2")))
        assertEquals(listOf("r1", "r2"), s.document.interactions.map { it.id })
        assertIs<EditResult.Success>(s.updateInteraction(rule("r1", "معدّل")))
        assertEquals("معدّل", s.document.interactions.first().name)
        assertIs<EditResult.Success>(s.removeInteraction("r1"))
        assertEquals(listOf("r2"), s.document.interactions.map { it.id })
    }

    @Test
    fun invalidOperationsFailWithClearCodesAndLeaveDocumentUntouched() {
        val s = session()
        s.addInteraction(rule("r1"))
        val before = s.document
        assertEquals(EditError.DUPLICATE_ID, s.addInteraction(rule("r1")).error())
        assertEquals(EditError.ELEMENT_NOT_FOUND, s.updateInteraction(rule("ghost")).error())
        assertEquals(EditError.ELEMENT_NOT_FOUND, s.removeInteraction("ghost").error())
        assertEquals(EditError.INVALID_INTERACTION, s.addInteraction(rule("r2", value = "x".repeat(501))).error())
        assertEquals(EditError.INVALID_INTERACTION, s.addInteraction(rule(" ")).error())
        assertEquals(before, s.document)
    }

    @Test
    fun tooManyInteractionsIsRejected() {
        var doc = PageDocument.empty("ع", ids = IdGenerator { "root" })
        repeat(200) { doc = (DocumentEditor.addInteraction(doc, rule("r$it")) as EditResult.Success).document }
        assertEquals(EditError.TOO_MANY_INTERACTIONS, DocumentEditor.addInteraction(doc, rule("extra")).error())
    }

    @Test
    fun interactionEditsAreUndoableAndBumpRevision() {
        val s = session()
        val rev = s.revision
        s.addInteraction(rule("r1"))
        assertEquals(rev + 1, s.revision)
        assertTrue(s.undo())
        assertTrue(s.document.interactions.isEmpty())
        assertTrue(s.redo())
        assertEquals(1, s.document.interactions.size)
    }

    @Test
    fun newInteractionIdsAreUniqueAndNeverClashWithElementIds() {
        val s = session()
        s.addElement(ElementType.BUTTON)
        val a = s.newInteractionId()
        val b = s.newInteractionId()
        assertFalse(a == b)
        assertTrue(s.document.find(a) == null)
    }

    @Test
    fun deletingAnElementKeepsInteractionsForTheEditorToFlag() {
        val s = session()
        s.addElement(ElementType.BUTTON)
        val btnId = s.selectedId!!
        s.addInteraction(Interaction("r", "", Trigger(TriggerType.CLICK, btnId), true, emptyList(), listOf(Action(ActionType.MESSAGE, "", "x"))))
        s.removeSelected()
        assertEquals(1, s.document.interactions.size)
        assertTrue(com.arabicpagebuilder.core.interaction.InteractionValidator.check(s.document, s.document.interactions.first()).isNotEmpty())
    }
}

package com.arabicpagebuilder.core.edit

import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.IdGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryTest {

    @Test
    fun undoAndRedoWalkThroughStates() {
        val h = History(1)
        h.record(2)
        h.record(3)
        assertEquals(2, h.undo())
        assertEquals(1, h.undo())
        assertNull(h.undo())
        assertEquals(2, h.redo())
        assertEquals(3, h.redo())
        assertNull(h.redo())
        assertEquals(3, h.present)
    }

    @Test
    fun newRecordClearsRedo() {
        val h = History(1)
        h.record(2)
        h.undo()
        assertTrue(h.canRedo)
        h.record(9)
        assertFalse(h.canRedo)
        assertEquals(9, h.present)
        assertEquals(1, h.undo())
    }

    @Test
    fun identicalStateIsNotRecorded() {
        val h = History("a")
        h.record("a")
        assertFalse(h.canUndo)
    }

    @Test
    fun limitDropsOldestSteps() {
        val h = History(0, limit = 2)
        h.record(1)
        h.record(2)
        h.record(3)
        assertEquals(2, h.undo())
        assertEquals(1, h.undo())
        assertNull(h.undo())
    }

    @Test
    fun undoRedoWithRealDocumentEdits() {
        val ids = IdGenerator { "root" }
        val h = History(PageDocument.empty("t", ids = ids))
        val added = DocumentEditor.add(h.present, "root", Element("h", ElementType.HEADING))
        h.record((added as EditResult.Success).document)
        assertEquals(1, h.present.root.children.size)
        assertEquals(0, h.undo()?.root?.children?.size)
        assertEquals(1, h.redo()?.root?.children?.size)
    }
}

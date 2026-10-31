package com.arabicpagebuilder.core.canvas

import com.arabicpagebuilder.core.edit.EditError
import com.arabicpagebuilder.core.edit.EditResult
import com.arabicpagebuilder.core.generator.HtmlGenerator
import com.arabicpagebuilder.core.model.Action
import com.arabicpagebuilder.core.model.ActionType
import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.IdGenerator
import com.arabicpagebuilder.core.model.Interaction
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.Trigger
import com.arabicpagebuilder.core.model.TriggerType
import com.arabicpagebuilder.core.session.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditModeTest {

    private fun doc(interactions: List<Interaction> = emptyList(), vararg children: Element): PageDocument {
        val empty = PageDocument.empty("ع", ids = IdGenerator { "root" })
        return empty.copy(root = empty.root.copy(children = children.toList()), interactions = interactions)
    }

    private val sample = arrayOf(
        Element("hero", ElementType.SECTION, mapOf("background" to "#eee"), listOf(Element("t", ElementType.HEADING, mapOf("text" to "عنوان")))),
        Element("btn", ElementType.BUTTON, mapOf("text" to "تفاصيل")),
        Element("details", ElementType.SECTION, mapOf("hidden" to "true")),
        Element("f", ElementType.TEXT_FIELD, mapOf("label" to "الاسم", "name" to "n")),
    )

    @Test
    fun everyRenderedElementCarriesItsIdAndThePageIsWrappedInAStage() {
        val html = HtmlGenerator.generate(doc(emptyList(), *sample), editMode = true).indexHtml
        assertTrue(html.contains("<body class=\"e-root\" data-eid=\"root\">"))
        assertTrue(html.contains("<div id=\"__stage\">") && html.contains("</div>\n</body>"))
        for (id in listOf("hero", "t", "btn", "details", "f")) {
            assertEquals(1, Regex("data-eid=\"$id\"").findAll(html).count(), id)
        }
    }

    @Test
    fun hiddenElementsAreShownButMarkedInEditMode() {
        val edit = HtmlGenerator.generate(doc(emptyList(), *sample), editMode = true).indexHtml
        assertFalse(Regex("\\shidden[\\s>]").containsMatchIn(edit))
        assertTrue(edit.contains("<section data-eid=\"details\" data-hidden=\"1\" class=\"e-details\">"))
        val normal = HtmlGenerator.generate(doc(emptyList(), *sample)).indexHtml
        assertTrue(normal.contains("<section hidden class=\"e-details\">"))
        assertFalse(normal.contains("data-eid") || normal.contains("__stage"))
    }

    @Test
    fun editModeNeverEmitsScriptsAndKeepsStylesIdentical() {
        val rule = Interaction("r", "", Trigger(TriggerType.CLICK, "btn"), true, emptyList(), listOf(Action(ActionType.SHOW, "details")))
        val d = doc(listOf(rule), *sample)
        val edit = HtmlGenerator.generate(d, editMode = true)
        val normal = HtmlGenerator.generate(d)
        assertNull(edit.mainJs)
        assertFalse(edit.indexHtml.contains("<script"))
        assertEquals(listOf("index.html", "css/style.css"), edit.files.keys.toList())
        assertEquals(normal.styleCss, edit.styleCss)
        assertTrue(normal.mainJs != null)
        // الزر الذي له تفاعل يُرسم فعّالًا في وضع التحرير كما في الموقع
        assertTrue(edit.indexHtml.contains("<button data-eid=\"btn\" type=\"button\" class=\"e-btn btn\">تفاصيل</button>"))
    }

    @Test
    fun editModeDoesNotLeakInternalMarksIntoVisibleHtml() {
        val html = HtmlGenerator.generate(doc(emptyList(), *sample), editMode = true).indexHtml
        assertFalse(html.contains("__edit") || html.contains("__trigger"))
    }

    @Test
    fun nonRenderedElementsGetNoId() {
        val html = HtmlGenerator.generate(doc(emptyList(), Element("img", ElementType.IMAGE)), editMode = true).indexHtml
        assertFalse(html.contains("data-eid=\"img\"")) // صورة بلا مصدر لا تُرسم
    }

    @Test
    fun editorScriptIsStaticAndSafe() {
        for (bad in listOf("eval(", "innerHTML", "Function(", "document.write", "XMLHttpRequest", "fetch(", "localStorage", "addJavascriptInterface")) {
            assertFalse(EditorScript.RUNTIME.contains(bad), bad)
        }
        assertTrue(EditorScript.RUNTIME.contains("window.__apb") && EditorScript.RUNTIME.contains("api.rects") && EditorScript.RUNTIME.contains("api.setZoom") && EditorScript.RUNTIME.contains("api.setSize"))
        assertTrue(EditorScript.CSS.contains("animation: none"))
    }

    // ---- setProperties ذرّية ----

    private fun session(): EditorSession {
        var n = 0
        val ids = IdGenerator { "e${++n}" }
        return EditorSession(PageDocument.empty("ع", ids = ids), ids)
    }

    @Test
    fun setPropertiesChangesSeveralInOneUndoStep() {
        val s = session()
        s.addElement(ElementType.CARD)
        val before = s.document
        assertIs<EditResult.Success>(s.setProperties(mapOf("width" to "50%", "height" to "120px")))
        assertEquals("50%", s.selected!!.props["width"]); assertEquals("120px", s.selected!!.props["height"])
        assertTrue(s.undo())
        assertEquals(before, s.document) // خطوة واحدة تعيد الاثنين
    }

    @Test
    fun setPropertiesNullOrBlankRemovesAndFailureChangesNothing() {
        val s = session()
        s.addElement(ElementType.CARD)
        s.setProperties(mapOf("width" to "50%", "height" to "10px"))
        s.setProperties(mapOf("width" to null, "height" to "  "))
        assertNull(s.selected!!.props["width"]); assertNull(s.selected!!.props["height"])
        val doc = s.document
        val r = s.setProperties(mapOf("width" to "40px", " " to "x"))
        assertEquals(EditError.INVALID_PROPERTY_KEY, assertIs<EditResult.Failure>(r).error)
        assertEquals(doc, s.document)
        s.select(null)
        assertEquals(EditError.ELEMENT_NOT_FOUND, assertIs<EditResult.Failure>(s.setProperties(mapOf("width" to "1px"))).error)
    }
}

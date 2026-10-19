package com.arabicpagebuilder.core.storage

import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.TextDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectFormatTest {

    private fun docOf(root: Element, title: String = "عنوان", dir: TextDirection = TextDirection.RTL) =
        PageDocument(title, dir, root)

    private fun root(vararg children: Element) = Element("root", ElementType.PAGE_ROOT, emptyMap(), children.toList())

    private fun data(doc: PageDocument, name: String = "مشروعي") = ProjectData(name, 1000L, 2000L, doc)

    private fun failure(text: String, maxChars: Int = ProjectFormat.MAX_CHARS) =
        assertIs<DecodeResult.Failure>(ProjectFormat.decode(text, maxChars))

    private val simple = data(
        docOf(
            root(
                Element("sec", ElementType.SECTION, emptyMap(), listOf(Element("h", ElementType.HEADING, mapOf("text" to "مرحبًا", "level" to "2")))),
                Element("p", ElementType.PARAGRAPH, mapOf("text" to "سطر1\nسطر2")),
            ),
        ),
    )

    @Test
    fun roundTripPreservesEverythingIncludingSpecialText() {
        val nasty = "عربي English \"quotes\" 'single' \\ back </script> & emoji 😀 \u0644\u064E tab\t"
        val d = data(docOf(root(Element("p", ElementType.PARAGRAPH, mapOf("text" to nasty))), title = "عنوان \"خاص\"", dir = TextDirection.LTR), name = "اسم <b>")
        val back = assertIs<DecodeResult.Success>(ProjectFormat.decode(ProjectFormat.encode(d)))
        assertEquals(d, back.data)
        assertEquals(simple, assertIs<DecodeResult.Success>(ProjectFormat.decode(ProjectFormat.encode(simple))).data)
    }

    @Test
    fun malformedJsonIsRejected() {
        for (bad in listOf("", "not json", "{", "{\"schemaVersion\":1}", "[]", "null")) {
            assertEquals(FormatError.MALFORMED_JSON, failure(bad).error, bad)
        }
    }

    @Test
    fun unsupportedSchemaVersionIsRejected() {
        val json = ProjectFormat.encode(simple).replace("\"schemaVersion\":1", "\"schemaVersion\":999")
        assertEquals(FormatError.UNSUPPORTED_SCHEMA_VERSION, failure(json).error)
    }

    @Test
    fun unknownElementTypeIsRejected() {
        val json = ProjectFormat.encode(simple).replace("\"type\":\"HEADING\"", "\"type\":\"WIDGET\"")
        assertEquals(FormatError.UNKNOWN_ELEMENT_TYPE, failure(json).error)
    }

    @Test
    fun invalidDirectionAndRootTypeAreRejected() {
        val badDir = ProjectFormat.encode(simple).replace("\"direction\":\"RTL\"", "\"direction\":\"UP\"")
        assertEquals(FormatError.INVALID_STRUCTURE, failure(badDir).error)
        val badRoot = ProjectFormat.encode(simple).replace("\"type\":\"PAGE_ROOT\"", "\"type\":\"CONTAINER\"")
        assertEquals(FormatError.INVALID_STRUCTURE, failure(badRoot).error)
    }

    @Test
    fun duplicateIdsAreRejected() {
        val d = data(docOf(root(Element("a", ElementType.PARAGRAPH), Element("a", ElementType.PARAGRAPH))))
        assertEquals(FormatError.INVALID_STRUCTURE, failure(ProjectFormat.encode(d)).error)
    }

    @Test
    fun leafWithChildrenAndNestedRootTypeAreRejected() {
        val leaf = data(docOf(root(Element("i", ElementType.IMAGE, emptyMap(), listOf(Element("x", ElementType.PARAGRAPH))))))
        assertEquals(FormatError.INVALID_STRUCTURE, failure(ProjectFormat.encode(leaf)).error)
        val nestedRoot = data(docOf(root(Element("r2", ElementType.PAGE_ROOT))))
        assertEquals(FormatError.INVALID_STRUCTURE, failure(ProjectFormat.encode(nestedRoot)).error)
    }

    @Test
    fun tooDeepTreeIsRejected() {
        var node = Element("leaf", ElementType.PARAGRAPH)
        for (i in 1..100) node = Element("c$i", ElementType.CONTAINER, emptyMap(), listOf(node))
        val d = data(docOf(root(node)))
        assertEquals(FormatError.INVALID_STRUCTURE, failure(ProjectFormat.encode(d)).error)
    }

    @Test
    fun tooManyNodesAndOversizedValuesAreRejected() {
        val many = data(docOf(root(*Array(ProjectFormat.MAX_NODES + 1) { Element("n$it", ElementType.PARAGRAPH) })))
        assertEquals(FormatError.INVALID_STRUCTURE, failure(ProjectFormat.encode(many)).error)
        val huge = data(docOf(root(Element("p", ElementType.PARAGRAPH, mapOf("text" to "x".repeat(ProjectFormat.MAX_PROP_VALUE_LENGTH + 1))))))
        assertEquals(FormatError.INVALID_STRUCTURE, failure(ProjectFormat.encode(huge)).error)
    }

    @Test
    fun bracketBombAndOversizedInputAreRejectedWithoutCrashing() {
        assertEquals(FormatError.INVALID_STRUCTURE, failure("[".repeat(100_000)).error)
        assertEquals(FormatError.INVALID_STRUCTURE, failure("{\"a\":".repeat(5_000)).error)
        assertEquals(FormatError.TOO_LARGE, failure(ProjectFormat.encode(simple), maxChars = 10).error)
    }

    @Test
    fun unknownExtraKeysAreIgnored() {
        val json = ProjectFormat.encode(simple).replaceFirst("{", "{\"extra\":1,")
        assertTrue(ProjectFormat.decode(json) is DecodeResult.Success)
    }
}

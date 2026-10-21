package com.arabicpagebuilder.core.generator

import com.arabicpagebuilder.core.edit.DocumentEditor
import com.arabicpagebuilder.core.edit.EditResult
import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.IdGenerator
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.TextDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HtmlGeneratorTest {

    private fun doc(vararg children: Element, title: String = "عنوان", dir: TextDirection = TextDirection.RTL): PageDocument {
        val empty = PageDocument.empty(title, dir, IdGenerator { "root" })
        return empty.copy(root = empty.root.copy(children = children.toList()))
    }

    private fun el(id: String, type: ElementType, props: Map<String, String> = emptyMap(), vararg children: Element) =
        Element(id, type, props, children.toList())

    /** فحص توازن الوسوم المستخدمة في مخرجاتنا (ليس محلل HTML كاملًا). */
    private fun assertBalancedTags(html: String) {
        val void = setOf("meta", "link", "img", "br", "hr", "input")
        val stack = ArrayDeque<String>()
        for (m in Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)[^>]*>").findAll(html)) {
            val closing = m.groupValues[1] == "/"
            val name = m.groupValues[2].lowercase()
            if (name in void) continue
            if (closing) {
                assertEquals(name, stack.removeLastOrNull(), "وسم إغلاق غير متوازن: $name")
            } else {
                stack.addLast(name)
            }
        }
        assertTrue(stack.isEmpty(), "وسوم لم تُغلق: $stack")
    }

    @Test
    fun documentShellHasRtlLangCharsetViewportAndStylesheet() {
        val html = HtmlGenerator.generate(doc()).indexHtml
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<html lang=\"ar\" dir=\"rtl\">"))
        assertTrue(html.contains("<meta charset=\"utf-8\">"))
        assertTrue(html.contains("name=\"viewport\""))
        assertTrue(html.contains("<link rel=\"stylesheet\" href=\"css/style.css\">"))
        assertBalancedTags(html)
    }

    @Test
    fun ltrDocumentUsesEnglishLangAndLtr() {
        val html = HtmlGenerator.generate(doc(dir = TextDirection.LTR)).indexHtml
        assertTrue(html.contains("<html lang=\"en\" dir=\"ltr\">"))
    }

    @Test
    fun producesOnlyIndexAndCssAndNoJavascript() {
        val site = HtmlGenerator.generate(doc(el("h", ElementType.HEADING, mapOf("text" to "x"))))
        assertEquals(listOf("index.html", "css/style.css"), site.files.keys.toList())
        assertFalse(site.indexHtml.contains("<script"))
    }

    @Test
    fun specialCharactersInTextAreEscapedAndArabicIsPreserved() {
        val nasty = "مرحبا <script>alert(1)</script> & \"q\" 'a' Hello"
        val html = HtmlGenerator.generate(doc(el("p", ElementType.PARAGRAPH, mapOf("text" to nasty)))).indexHtml
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("مرحبا &lt;script&gt;alert(1)&lt;/script&gt; &amp; \"q\" 'a' Hello"))
    }

    @Test
    fun titleIsEscapedAndBlankTitleGetsDefault() {
        assertTrue(HtmlGenerator.generate(doc(title = "a<b>&")).indexHtml.contains("<title>a&lt;b&gt;&amp;</title>"))
        assertTrue(HtmlGenerator.generate(doc(title = "  ")).indexHtml.contains("<title>صفحة</title>"))
    }

    @Test
    fun newlinesBecomeBrAndHeadingLevelIsClamped() {
        val site = HtmlGenerator.generate(
            doc(
                el("p", ElementType.PARAGRAPH, mapOf("text" to "سطر1\nسطر2")),
                el("h", ElementType.HEADING, mapOf("text" to "t", "level" to "9")),
                el("h2", ElementType.HEADING, mapOf("text" to "t", "level" to "2")),
            ),
        )
        assertTrue(site.indexHtml.contains("سطر1<br>سطر2"))
        assertTrue(site.indexHtml.contains("<h3 class=\"e-h\">t</h3>"))
        assertTrue(site.indexHtml.contains("<h2 class=\"e-h2\">t</h2>"))
    }

    @Test
    fun linkGetsCorrectHrefAndExternalGetsRel() {
        val html = HtmlGenerator.generate(
            doc(
                el("a", ElementType.LINK, mapOf("text" to "موقع", "href" to "https://example.com/a?x=1&y=2")),
                el("b", ElementType.LINK, mapOf("text" to "بريد", "href" to "mailto:me@example.com")),
                el("c", ElementType.LINK, mapOf("text" to "داخلي", "href" to "about.html")),
            ),
        ).indexHtml
        assertTrue(html.contains("href=\"https://example.com/a?x=1&amp;y=2\" rel=\"noopener noreferrer\""))
        assertTrue(html.contains("href=\"mailto:me@example.com\">"))
        assertTrue(html.contains("href=\"about.html\">"))
    }

    @Test
    fun dangerousLinksAreDroppedWithWarning() {
        val site = HtmlGenerator.generate(
            doc(
                el("a", ElementType.LINK, mapOf("text" to "x", "href" to "javascript:alert(1)")),
                el("b", ElementType.LINK, mapOf("text" to "y", "href" to "data:text/html,hi")),
                el("c", ElementType.LINK, mapOf("text" to "z", "href" to "//evil.com")),
            ),
        )
        // في <head> يوجد <link href="css/style.css"> مشروع، لذا نفحص وسوم <a> والقيم الخطرة بدل أي "href=".
        assertFalse(site.indexHtml.contains("<a "))
        assertFalse(site.indexHtml.contains("javascript:"))
        assertFalse(site.indexHtml.contains("evil.com"))
        assertEquals(3, site.warnings.count { it.code == WarningCode.INVALID_LINK })
    }

    @Test
    fun buttonWithoutActionIsDisabledAndWarned_buttonWithHrefIsAnchor() {
        val site = HtmlGenerator.generate(
            doc(
                el("b1", ElementType.BUTTON, mapOf("text" to "بلا إجراء")),
                el("b2", ElementType.BUTTON, mapOf("text" to "اذهب", "href" to "https://example.com")),
            ),
        )
        assertTrue(site.indexHtml.contains("<button type=\"button\" class=\"e-b1 btn\" disabled>بلا إجراء</button>"))
        assertTrue(site.indexHtml.contains("<a class=\"e-b2 btn\" href=\"https://example.com\""))
        assertTrue(site.warnings.any { it.elementId == "b1" && it.code == WarningCode.BUTTON_WITHOUT_ACTION })
    }

    @Test
    fun imageRendersEscapedAltAndWarnsOnMissingAlt() {
        val site = HtmlGenerator.generate(
            doc(
                el("i1", ElementType.IMAGE, mapOf("src" to "assets/images/a.png", "alt" to "صورة \"عائلة\"")),
                el("i2", ElementType.IMAGE, mapOf("src" to "assets/images/b.png")),
            ),
        )
        assertTrue(site.indexHtml.contains("src=\"assets/images/a.png\" alt=\"صورة &quot;عائلة&quot;\""))
        assertTrue(site.warnings.any { it.elementId == "i2" && it.code == WarningCode.MISSING_ALT })
    }

    @Test
    fun badOrMissingImageSourcesAreOmittedWithWarnings() {
        val site = HtmlGenerator.generate(
            doc(
                el("i1", ElementType.IMAGE, mapOf("src" to "/data/user/0/app/files/x.png")),
                el("i2", ElementType.IMAGE, mapOf("src" to "../secret.png")),
                el("i3", ElementType.IMAGE, mapOf("src" to "file:///sdcard/x.png")),
                el("i4", ElementType.IMAGE),
            ),
        )
        assertFalse(site.indexHtml.contains("<img"))
        assertEquals(3, site.warnings.count { it.code == WarningCode.INVALID_IMAGE_SOURCE })
        assertEquals(1, site.warnings.count { it.code == WarningCode.MISSING_IMAGE_SOURCE })
    }

    @Test
    fun nestedElementsKeepOrderAndBalance() {
        val tree = el(
            "sec", ElementType.SECTION, emptyMap(),
            el("row", ElementType.ROW, emptyMap(),
                el("c1", ElementType.COLUMN, emptyMap(), el("h", ElementType.HEADING, mapOf("text" to "أ"))),
                el("c2", ElementType.COLUMN, emptyMap(), el("card", ElementType.CARD, emptyMap(), el("p", ElementType.PARAGRAPH, mapOf("text" to "ب")))),
            ),
        )
        val html = HtmlGenerator.generate(doc(tree)).indexHtml
        assertBalancedTags(html)
        val order = listOf("e-sec", "e-row", "e-c1", "e-h\"", "e-c2", "e-card", "e-p\"").map { html.indexOf(it) }
        assertTrue(order.all { it >= 0 })
        assertEquals(order.sorted(), order)
    }

    @Test
    fun validStylesBecomeCssRulesAndAreSeparatedFromHtml() {
        val site = HtmlGenerator.generate(
            doc(el("h", ElementType.HEADING, mapOf("text" to "t", "color" to "#ff0000", "fontSize" to "2rem", "textAlign" to "center", "padding" to "8px 16px"))),
        )
        assertFalse(site.indexHtml.contains("style="))
        assertTrue(site.styleCss.contains(".e-h {"))
        assertTrue(site.styleCss.contains("color: #ff0000;"))
        assertTrue(site.styleCss.contains("font-size: 2rem;"))
        assertTrue(site.styleCss.contains("text-align: center;"))
        assertTrue(site.styleCss.contains("padding: 8px 16px;"))
        assertTrue(site.warnings.isEmpty())
    }

    @Test
    fun cssInjectionAttemptsAreRejected() {
        val site = HtmlGenerator.generate(
            doc(
                el(
                    "h", ElementType.HEADING,
                    mapOf(
                        "text" to "t",
                        "color" to "red; } body { display: none",
                        "background" to "url(http://evil.example/x)",
                        "width" to "100px; position:fixed",
                        "fontFamily" to "Arial, } x{",
                    ),
                ),
            ),
        )
        assertFalse(site.styleCss.contains("display: none"))
        assertFalse(site.styleCss.contains("evil.example"))
        assertFalse(site.styleCss.contains("position"))
        assertFalse(site.styleCss.contains(".e-h {"))
        assertEquals(4, site.warnings.count { it.code == WarningCode.INVALID_STYLE_VALUE })
    }

    @Test
    fun classNamesAreSafeEvenForOddIds() {
        val weird = "id with \"quotes\" & <tags> 'x'"
        val cls = HtmlGenerator.className(el(weird, ElementType.PARAGRAPH))
        assertTrue(cls.matches(Regex("^e-[A-Za-z0-9_-]+$")))
    }

    @Test
    fun outputIsDeterministic() {
        val d = doc(el("h", ElementType.HEADING, mapOf("text" to "t", "color" to "blue")), el("p", ElementType.PARAGRAPH, mapOf("text" to "x")))
        assertEquals(HtmlGenerator.generate(d), HtmlGenerator.generate(d))
    }

    @Test
    fun generatingFromEditedDocumentReflectsChanges() {
        var d = PageDocument.empty("t", ids = IdGenerator { "root" })
        d = (DocumentEditor.add(d, "root", el("h", ElementType.HEADING, mapOf("text" to "قديم"))) as EditResult.Success).document
        d = (DocumentEditor.setProperty(d, "h", "text", "جديد") as EditResult.Success).document
        val html = HtmlGenerator.generate(d).indexHtml
        assertTrue(html.contains("جديد"))
        assertFalse(html.contains("قديم"))
    }
}

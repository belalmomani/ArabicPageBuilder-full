package com.arabicpagebuilder.core.generator

import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.IdGenerator
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.session.ElementCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NewElementsGeneratorTest {

    private fun doc(vararg children: Element): PageDocument {
        val empty = PageDocument.empty("ع", ids = IdGenerator { "root" })
        return empty.copy(root = empty.root.copy(children = children.toList()))
    }

    private fun el(id: String, type: ElementType, props: Map<String, String> = emptyMap(), vararg children: Element) =
        Element(id, type, props, children.toList())

    private fun gen(vararg children: Element) = HtmlGenerator.generate(doc(*children))

    private fun GeneratedSite.codes(): List<WarningCode> = warnings.map { it.code }

    private fun assertBalanced(html: String) {
        val void = setOf("meta", "link", "img", "br", "hr", "input")
        val stack = ArrayDeque<String>()
        for (m in Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)[^>]*>").findAll(html)) {
            val name = m.groupValues[2].lowercase()
            if (name in void) continue
            if (m.groupValues[1] == "/") assertEquals(name, stack.removeLastOrNull(), "غير متوازن: $name") else stack.addLast(name)
        }
        assertTrue(stack.isEmpty(), "لم تُغلق: $stack")
    }

    // ---- النصوص ----

    @Test
    fun inlineTextTypesUseSemanticTags() {
        val html = gen(
            el("t", ElementType.TEXT, mapOf("text" to "نص")),
            el("b", ElementType.BOLD, mapOf("text" to "عريض")),
            el("i", ElementType.ITALIC, mapOf("text" to "مائل")),
            el("u", ElementType.UNDERLINE, mapOf("text" to "مسطر <")),
        ).indexHtml
        assertTrue(html.contains("<span class=\"e-t\">نص</span>"))
        assertTrue(html.contains("<strong class=\"e-b\">عريض</strong>"))
        assertTrue(html.contains("<em class=\"e-i\">مائل</em>"))
        assertTrue(html.contains("<u class=\"e-u\">مسطر &lt;</u>"))
    }

    @Test
    fun listsRenderOneItemPerNonBlankLineAndEscape() {
        val site = gen(
            el("l1", ElementType.LIST_UNORDERED, mapOf("items" to "أ\n\n ب \nج<")),
            el("l2", ElementType.LIST_ORDERED, mapOf("items" to "واحد\nاثنان")),
            el("l3", ElementType.LIST_ORDERED),
        )
        val html = site.indexHtml
        assertTrue(html.contains("<ul class=\"e-l1\">"))
        assertTrue(html.contains("<li>أ</li>") && html.contains("<li>ب</li>") && html.contains("<li>ج&lt;</li>"))
        assertTrue(html.contains("<ol class=\"e-l2\">"))
        assertTrue(html.contains("<ol class=\"e-l3\"></ol>"))
        assertEquals(5, Regex("<li>").findAll(html).count())
        assertBalanced(html)
    }

    @Test
    fun quoteDividerAndSpacer() {
        val site = gen(
            el("q", ElementType.QUOTE, mapOf("text" to "اقتباس", "cite" to "المصدر")),
            el("q2", ElementType.QUOTE, mapOf("text" to "بلا مصدر")),
            el("d", ElementType.DIVIDER),
            el("s", ElementType.SPACER, mapOf("height" to "40px")),
        )
        val html = site.indexHtml
        assertTrue(html.contains("<blockquote class=\"e-q\">"))
        assertTrue(html.contains("<p>اقتباس</p>") && html.contains("<footer>المصدر</footer>"))
        assertEquals(1, Regex("<footer>").findAll(html).count())
        assertTrue(html.contains("<hr class=\"e-d\">"))
        assertTrue(html.contains("<div class=\"e-s spacer\" aria-hidden=\"true\"></div>"))
        assertTrue(site.styleCss.contains(".e-s {\n  height: 40px;"))
        assertBalanced(html)
    }

    // ---- التخطيط ----

    @Test
    fun headerFooterGridAndGalleryContainers() {
        val site = gen(
            el("h", ElementType.HEADER, emptyMap(), el("p", ElementType.PARAGRAPH, mapOf("text" to "x"))),
            el("g", ElementType.GRID, mapOf("columns" to "4", "gap" to "8px"), el("c", ElementType.CARD)),
            el("ga", ElementType.GALLERY, emptyMap(), el("i", ElementType.IMAGE, mapOf("src" to "assets/images/a.png", "alt" to "a"))),
            el("f", ElementType.FOOTER),
        )
        val html = site.indexHtml
        assertTrue(html.contains("<header class=\"e-h\">"))
        assertTrue(html.contains("<div class=\"e-g grid\">"))
        assertTrue(html.contains("<div class=\"e-ga gallery\">"))
        assertTrue(html.contains("<footer class=\"e-f\"></footer>"))
        assertTrue(site.styleCss.contains("grid-template-columns: repeat(4, minmax(0, 1fr));"))
        assertTrue(site.styleCss.contains("gap: 8px;"))
        assertBalanced(html)
    }

    @Test
    fun invalidGridColumnsIsRejectedWithWarning() {
        val site = gen(el("g", ElementType.GRID, mapOf("columns" to "9; x")))
        assertTrue(WarningCode.INVALID_STYLE_VALUE in site.codes())
        assertFalse(site.styleCss.contains("repeat(9"))
    }

    // ---- الوسائط ----

    @Test
    fun videoAndAudioRenderWithControlsAndSafeSources() {
        val html = gen(
            el("v", ElementType.VIDEO, mapOf("src" to "assets/media/clip.mp4")),
            el("a", ElementType.AUDIO, mapOf("src" to "https://example.com/a.mp3")),
        ).indexHtml
        assertTrue(html.contains("<video class=\"e-v\" src=\"assets/media/clip.mp4\" controls preload=\"metadata\"></video>"))
        assertTrue(html.contains("<audio class=\"e-a\" src=\"https://example.com/a.mp3\" controls preload=\"metadata\"></audio>"))
    }

    @Test
    fun badMediaSourcesAreOmittedWithWarnings() {
        val site = gen(
            el("v1", ElementType.VIDEO),
            el("v2", ElementType.VIDEO, mapOf("src" to "javascript:alert(1)")),
            el("a1", ElementType.AUDIO, mapOf("src" to "/data/user/0/x.mp3")),
            el("a2", ElementType.AUDIO, mapOf("src" to "../x.mp3")),
        )
        assertFalse(site.indexHtml.contains("<video") || site.indexHtml.contains("<audio"))
        assertEquals(1, site.codes().count { it == WarningCode.MISSING_MEDIA_SOURCE })
        assertEquals(3, site.codes().count { it == WarningCode.INVALID_MEDIA_SOURCE })
    }

    // ---- الأزرار ----

    @Test
    fun downloadButtonUsesDownloadAttributeOrIsDisabled() {
        val site = gen(
            el("d1", ElementType.DOWNLOAD_BUTTON, mapOf("text" to "نزّل", "href" to "assets/files/cv.pdf")),
            el("d2", ElementType.DOWNLOAD_BUTTON, mapOf("text" to "نزّل", "href" to "https://example.com/f.zip")),
            el("d3", ElementType.DOWNLOAD_BUTTON, mapOf("text" to "بلا ملف")),
            el("d4", ElementType.DOWNLOAD_BUTTON, mapOf("text" to "خطر", "href" to "javascript:alert(1)")),
        )
        val html = site.indexHtml
        assertTrue(html.contains("<a class=\"e-d1 btn\" href=\"assets/files/cv.pdf\" download>نزّل</a>"))
        assertTrue(html.contains("href=\"https://example.com/f.zip\" download rel=\"noopener noreferrer\">"))
        assertTrue(html.contains("<button type=\"button\" class=\"e-d3 btn\" disabled>بلا ملف</button>"))
        assertFalse(html.contains("javascript:"))
        assertTrue(WarningCode.INVALID_LINK in site.codes())
    }

    @Test
    fun callButtonNormalizesNumberOrIsDisabled() {
        val site = gen(
            el("c1", ElementType.CALL_BUTTON, mapOf("text" to "اتصل", "phone" to "+962 79 000-0000")),
            el("c2", ElementType.CALL_BUTTON, mapOf("text" to "اتصل", "phone" to "abc")),
            el("c3", ElementType.CALL_BUTTON, mapOf("text" to "اتصل")),
        )
        assertTrue(site.indexHtml.contains("<a class=\"e-c1 btn\" href=\"tel:+962790000000\">اتصل</a>"))
        assertEquals(2, site.codes().count { it == WarningCode.INVALID_PHONE })
        assertEquals(2, Regex("disabled").findAll(site.indexHtml).count())
    }

    @Test
    fun whatsappButtonBuildsWaMeLinkWithEncodedMessage() {
        val site = gen(
            el("w1", ElementType.WHATSAPP_BUTTON, mapOf("text" to "واتساب", "phone" to "+962790000000", "message" to "مرحبا")),
            el("w2", ElementType.WHATSAPP_BUTTON, mapOf("text" to "واتساب", "phone" to "962790000000")),
            el("w3", ElementType.WHATSAPP_BUTTON, mapOf("text" to "واتساب", "phone" to "12")),
            el("w4", ElementType.WHATSAPP_BUTTON, mapOf("text" to "واتساب", "phone" to "962790000000", "message" to "a b&c")),
        )
        val html = site.indexHtml
        assertTrue(html.contains("href=\"https://wa.me/962790000000?text=%D9%85%D8%B1%D8%AD%D8%A8%D8%A7\" rel=\"noopener noreferrer\""))
        assertTrue(html.contains("href=\"https://wa.me/962790000000\" rel="))
        assertTrue(html.contains("?text=a%20b%26c"))
        assertEquals(1, site.codes().count { it == WarningCode.INVALID_PHONE })
    }

    @Test
    fun submitButtonWorksOnlyInsideForm() {
        val inside = gen(el("f", ElementType.FORM, emptyMap(), el("s", ElementType.SUBMIT_BUTTON, mapOf("text" to "إرسال"))))
        assertTrue(inside.indexHtml.contains("<button type=\"submit\" class=\"e-s btn\">إرسال</button>"))
        assertFalse(WarningCode.SUBMIT_OUTSIDE_FORM in inside.codes())
        val outside = gen(el("s", ElementType.SUBMIT_BUTTON, mapOf("text" to "إرسال")))
        assertTrue(outside.indexHtml.contains("<button type=\"button\" class=\"e-s btn\" disabled>إرسال</button>"))
        assertTrue(WarningCode.SUBMIT_OUTSIDE_FORM in outside.codes())
    }

    @Test
    fun submitNestedDeepInsideFormStillCountsAsInside() {
        val site = gen(el("f", ElementType.FORM, emptyMap(), el("box", ElementType.CONTAINER, emptyMap(), el("s", ElementType.SUBMIT_BUTTON))))
        assertTrue(site.indexHtml.contains("type=\"submit\""))
        assertBalanced(site.indexHtml)
    }

    // ---- النماذج ----

    @Test
    fun formActionMustBeHttpsAndMissingActionWarns() {
        val ok = gen(el("f", ElementType.FORM, mapOf("action" to "https://formspree.io/f/abc")))
        assertTrue(ok.indexHtml.contains("<form class=\"e-f form\" action=\"https://formspree.io/f/abc\" method=\"post\"></form>"))
        assertTrue(ok.warnings.isEmpty())

        val none = gen(el("f", ElementType.FORM))
        assertTrue(none.indexHtml.contains("<form class=\"e-f form\" method=\"post\"></form>"))
        assertEquals(listOf(WarningCode.FORM_WITHOUT_ACTION), none.codes())

        val http = gen(el("f", ElementType.FORM, mapOf("action" to "http://example.com/x")))
        assertFalse(http.indexHtml.contains("action="))
        assertEquals(listOf(WarningCode.INVALID_FORM_ACTION), http.codes())

        val js = gen(el("f", ElementType.FORM, mapOf("action" to "javascript:alert(1)")))
        assertFalse(js.indexHtml.contains("javascript:"))

        val get = gen(el("f", ElementType.FORM, mapOf("method" to "get", "action" to "https://a.com/s")))
        assertTrue(get.indexHtml.contains("method=\"get\""))
    }

    @Test
    fun inputFieldsRenderLabelNamePlaceholderAndRequired() {
        val html = gen(
            el("t", ElementType.TEXT_FIELD, mapOf("label" to "الاسم", "name" to "name", "placeholder" to "اكتب \"هنا\"", "required" to "true")),
            el("e", ElementType.EMAIL_FIELD, mapOf("label" to "بريد", "name" to "email")),
            el("n", ElementType.NUMBER_FIELD, mapOf("label" to "رقم", "name" to "age", "required" to "false")),
        ).indexHtml
        assertTrue(html.contains("<label class=\"e-t field\"><span>الاسم</span><input type=\"text\" name=\"name\" placeholder=\"اكتب &quot;هنا&quot;\" required></label>"))
        assertTrue(html.contains("<input type=\"email\" name=\"email\"></label>"))
        assertTrue(html.contains("<input type=\"number\" name=\"age\"></label>"))
        assertBalanced(html)
    }

    @Test
    fun invalidFieldNamesAreOmittedWithWarning() {
        val site = gen(
            el("a", ElementType.TEXT_FIELD, mapOf("label" to "x", "name" to "1bad")),
            el("b", ElementType.TEXT_FIELD, mapOf("label" to "y", "name" to "a b")),
            el("c", ElementType.TEXT_FIELD, mapOf("label" to "z")),
            el("d", ElementType.TEXT_FIELD, mapOf("label" to "w", "name" to "ok_name-1")),
        )
        assertEquals(3, site.codes().count { it == WarningCode.INVALID_FIELD_NAME })
        assertFalse(site.indexHtml.contains("name=\"1bad\"") || site.indexHtml.contains("name=\"a b\""))
        assertTrue(site.indexHtml.contains("name=\"ok_name-1\""))
    }

    @Test
    fun labelsAndOptionsAreEscaped() {
        val html = gen(
            el("t", ElementType.TEXT_FIELD, mapOf("label" to "a<b>&", "name" to "x")),
            el("s", ElementType.SELECT, mapOf("label" to "اختر", "name" to "choice", "options" to "أ\nب<\n\n\"ج\"")),
        ).indexHtml
        assertTrue(html.contains("<span>a&lt;b&gt;&amp;</span>"))
        assertTrue(html.contains("<select name=\"choice\"><option value=\"أ\">أ</option><option value=\"ب&lt;\">ب&lt;</option><option value=\"&quot;ج&quot;\">\"ج\"</option></select>"))
        assertBalanced(html)
    }

    @Test
    fun checkboxAndTextareaRender() {
        val html = gen(
            el("c", ElementType.CHECKBOX, mapOf("label" to "أوافق", "name" to "agree", "required" to "true")),
            el("m", ElementType.TEXTAREA_FIELD, mapOf("label" to "الرسالة", "name" to "message")),
        ).indexHtml
        assertTrue(html.contains("<label class=\"e-c field check\"><input type=\"checkbox\" name=\"agree\" value=\"yes\" required><span>أوافق</span></label>"))
        assertTrue(html.contains("<label class=\"e-m field\"><span>الرسالة</span><textarea rows=\"4\" name=\"message\"></textarea></label>"))
    }

    @Test
    fun contactFormPresetGeneratesCompleteFormWithOnlyTheNoServerWarning() {
        var n = 0
        val preset = assertNotNull(ElementCatalog.buildPreset(ElementCatalog.PRESET_CONTACT_FORM, IdGenerator { "p${++n}" }))
        val site = gen(preset)
        assertEquals(listOf(WarningCode.FORM_WITHOUT_ACTION), site.codes())
        assertTrue(site.indexHtml.contains("type=\"email\"") && site.indexHtml.contains("<textarea") && site.indexHtml.contains("type=\"submit\""))
        assertEquals(3, Regex(" required>").findAll(site.indexHtml).count())
        assertBalanced(site.indexHtml)
    }

    @Test
    fun everyElementTypeGeneratesBalancedHtmlWithoutCrashing() {
        var n = 0
        val ids = IdGenerator { "x${++n}" }
        val all = ElementCatalog.categories.flatMap { it.types }.map { ElementCatalog.defaultElement(it, ids) }
        val site = gen(*all.toTypedArray())
        assertBalanced(site.indexHtml)
        assertTrue(site.indexHtml.isNotBlank())
    }

    @Test
    fun noJavascriptEverAppearsInOutput() {
        var n = 0
        val ids = IdGenerator { "x${++n}" }
        val all = ElementCatalog.categories.flatMap { it.types }.map { ElementCatalog.defaultElement(it, ids) }
        val site = gen(*all.toTypedArray())
        assertFalse(site.indexHtml.contains("<script") || site.indexHtml.contains("javascript:") || Regex("\\son[a-z]+=").containsMatchIn(site.indexHtml))
        assertEquals(listOf("index.html", "css/style.css"), site.files.keys.toList())
    }
}

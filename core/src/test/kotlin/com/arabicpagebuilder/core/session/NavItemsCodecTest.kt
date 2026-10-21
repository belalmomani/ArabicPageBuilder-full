package com.arabicpagebuilder.core.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NavItemsCodecTest {

    private val sample = "الرئيسية|#\nالخدمات|https://example.com/s\n- فرعي أول|about.html\n- فرعي ثان|#\nاتصل|mailto:a@b.co"

    @Test
    fun parseReadsTextHrefAndSubFlag() {
        val e = NavItemsCodec.parse(sample)
        assertEquals(5, e.size)
        assertEquals(NavEntry("الرئيسية", "#", false), e[0])
        assertEquals(NavEntry("الخدمات", "https://example.com/s", false), e[1])
        assertEquals(NavEntry("فرعي أول", "about.html", true), e[2])
        assertEquals(true, e[3].isSub)
        assertEquals(false, e[4].isSub)
    }

    @Test
    fun serializeThenParseRoundTrips() {
        assertEquals(sample, NavItemsCodec.serialize(NavItemsCodec.parse(sample)))
        val entries = NavItemsCodec.parse(sample)
        assertEquals(entries, NavItemsCodec.parse(NavItemsCodec.serialize(entries)))
    }

    @Test
    fun blankLinesAndEmptyTextsAreDroppedAndMissingHrefBecomesHash() {
        val e = NavItemsCodec.parse("\n  \nأ\n|#\n- \nب|\n")
        assertEquals(listOf("أ", "ب"), e.map { it.text })
        assertEquals("", e[0].href)                       // لا رابط مكتوب
        assertEquals("أ|#\nب|#", NavItemsCodec.serialize(e)) // يُكتب # عند الحفظ
    }

    @Test
    fun firstEntryCanNeverBeSub() {
        assertEquals(false, NavItemsCodec.parse("- أ|#\n- ب|#")[0].isSub)
        assertEquals("أ|#\n- ب|#", NavItemsCodec.serialize(NavItemsCodec.parse("- أ|#\n- ب|#")))
        val moved = NavItemsCodec.move(NavItemsCodec.parse("أ|#\n- ب|#"), 1, -1) // الفرعي يصير أول
        assertEquals(false, moved[0].isSub)
    }

    @Test
    fun separatorsAndNewlinesInUserTextCannotBreakTheFormat() {
        val e = NavItemsCodec.update(listOf(NavEntry("أ", "#")), 0, text = "اسم|فيه\nسطر", href = "about.html\n- x")
        assertEquals(1, NavItemsCodec.parse(NavItemsCodec.serialize(e)).size)
        assertEquals("اسم فيه سطر", e[0].text)
        assertEquals(false, NavItemsCodec.serialize(e).lines().size > 1)
    }

    @Test
    fun addRemoveMoveAndUpdateRespectBounds() {
        var e = NavItemsCodec.parse("أ|#\nب|#\nج|#")
        e = NavItemsCodec.add(e)
        assertEquals(NavItemsCodec.DEFAULT_TEXT, e.last().text)
        assertEquals("#", e.last().href)
        e = NavItemsCodec.add(e, "فرع", "x.html", isSub = true)
        assertEquals(true, e.last().isSub)

        val moved = NavItemsCodec.move(e, 2, -1)
        assertEquals(listOf("أ", "ج", "ب"), moved.take(3).map { it.text })
        assertEquals(e, NavItemsCodec.move(e, 0, -1))      // أول عنصر لا يصعد
        assertEquals(e, NavItemsCodec.move(e, e.lastIndex, 1))
        assertEquals(e, NavItemsCodec.move(e, 99, 1))

        assertEquals(listOf("أ", "ج"), NavItemsCodec.remove(NavItemsCodec.parse("أ|#\nب|#\nج|#"), 1).map { it.text })
        assertEquals(e, NavItemsCodec.remove(e, -1))
        assertEquals(e, NavItemsCodec.update(e, 50, text = "x"))

        val toggled = NavItemsCodec.update(e, 1, isSub = true)
        assertEquals(true, toggled[1].isSub)
        assertEquals(false, NavItemsCodec.update(e, 0, isSub = true)[0].isSub) // الأول يبقى رئيسيًا
    }

    @Test
    fun removingFirstPromotesNextSubItem() {
        val e = NavItemsCodec.remove(NavItemsCodec.parse("أ|#\n- ب|#\nج|#"), 0)
        assertEquals(listOf("ب", "ج"), e.map { it.text })
        assertEquals(false, e[0].isSub)
    }

    @Test
    fun addIsCappedAtMaxEntries() {
        var e = emptyList<NavEntry>()
        repeat(NavItemsCodec.MAX_ENTRIES + 5) { e = NavItemsCodec.add(e) }
        assertEquals(NavItemsCodec.MAX_ENTRIES, e.size)
    }

    @Test
    fun hrefValidation() {
        for (ok in listOf("", "#", "https://a.com", "mailto:a@b.co", "tel:+96279", "page.html", "#top")) assertNull(NavItemsCodec.hrefError(ok), ok)
        for (bad in listOf("javascript:alert(1)", "//evil.com", "../x", "data:text/html,x")) assertNotNull(NavItemsCodec.hrefError(bad), bad)
    }

    @Test
    fun generatorAndCodecAgreeOnTheDefaultMenu() {
        val default = ElementCatalog.defaultElement(com.arabicpagebuilder.core.model.ElementType.NAV_MENU, com.arabicpagebuilder.core.model.IdGenerator { "x" })
        val entries = NavItemsCodec.parse(default.props.getValue("items"))
        assertEquals(4, entries.size)
        assertEquals(default.props.getValue("items"), NavItemsCodec.serialize(entries))
    }
}

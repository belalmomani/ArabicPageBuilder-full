package com.arabicpagebuilder.core.session

import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.IdGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PropertySpecsTest {

    private fun spec(type: ElementType, key: String) = PropertySpecs.forType(type).first { it.key == key }

    @Test
    fun onlyApplicablePropertiesAreOfferedPerType() {
        val heading = PropertySpecs.forType(ElementType.HEADING).map { it.key }
        assertTrue("text" in heading && "level" in heading)
        assertFalse("src" in heading || "href" in heading || "padding" in heading)
        val image = PropertySpecs.forType(ElementType.IMAGE).map { it.key }
        assertTrue("src" in image && "alt" in image)
        assertFalse("text" in image || "fontSize" in image)
        val button = PropertySpecs.forType(ElementType.BUTTON).map { it.key }
        assertTrue("href" in button && "background" in button)
        val card = PropertySpecs.forType(ElementType.CARD).map { it.key }
        assertFalse("text" in card || "src" in card)
    }

    @Test
    fun everyElementTypeHasSpecsAndUniqueKeys() {
        for (t in ElementType.entries) {
            val keys = PropertySpecs.forType(t).map { it.key }
            assertTrue(keys.isNotEmpty(), t.name)
            assertEquals(keys.size, keys.toSet().size, t.name)
        }
    }

    @Test
    fun allStyleSpecKeysAreKnownToTheCssSanitizer() {
        val nonStyle = setOf("text", "level", "href", "src", "alt", "items", "cite", "phone", "message", "label", "name", "placeholder", "required", "options", "action", "method", "active", "itemStyle", "mobile", "sticky")
        for (t in ElementType.entries) for (s in PropertySpecs.forType(t)) {
            val known = com.arabicpagebuilder.core.generator.CssSanitizer.allKeys.contains(s.key)
            if (s.key !in nonStyle) assertTrue(known, "${t.name}.${s.key}")
        }
    }

    @Test
    fun emptyValueIsAlwaysValid() {
        for (t in ElementType.entries) for (s in PropertySpecs.forType(t)) assertNull(PropertySpecs.validate(s, "  "), s.key)
    }

    @Test
    fun urlValidation() {
        val href = spec(ElementType.LINK, "href")
        assertNull(PropertySpecs.validate(href, "https://example.com"))
        assertNull(PropertySpecs.validate(href, "mailto:a@b.co"))
        assertNotNull(PropertySpecs.validate(href, "javascript:alert(1)"))
        assertNotNull(PropertySpecs.validate(href, "//evil.com"))
    }

    @Test
    fun imageSourceValidation() {
        val src = spec(ElementType.IMAGE, "src")
        assertNull(PropertySpecs.validate(src, "assets/images/a.png"))
        assertNull(PropertySpecs.validate(src, "https://x.com/a.png"))
        assertNotNull(PropertySpecs.validate(src, "/data/user/0/a.png"))
        assertNotNull(PropertySpecs.validate(src, "file:///sdcard/a.png"))
    }

    @Test
    fun cssValueValidation() {
        val color = spec(ElementType.HEADING, "color")
        assertNull(PropertySpecs.validate(color, "#1a73e8"))
        assertNull(PropertySpecs.validate(color, "red"))
        assertNotNull(PropertySpecs.validate(color, "red; } body {"))
        val size = spec(ElementType.HEADING, "fontSize")
        assertNull(PropertySpecs.validate(size, "2rem"))
        assertNotNull(PropertySpecs.validate(size, "-1px"))
        assertNotNull(PropertySpecs.validate(size, "12"))
        val padding = spec(ElementType.CARD, "padding")
        assertNull(PropertySpecs.validate(padding, "8px 16px"))
        assertNotNull(PropertySpecs.validate(padding, "1 2 3 4 5"))
        val width = spec(ElementType.CARD, "width")
        assertNull(PropertySpecs.validate(width, "auto"))
    }

    @Test
    fun choiceValidationAndPresetColorsAreValid() {
        val level = spec(ElementType.HEADING, "level")
        assertNull(PropertySpecs.validate(level, "2"))
        assertNotNull(PropertySpecs.validate(level, "9"))
        val color = spec(ElementType.HEADING, "color")
        for (c in PropertySpecs.colorPresets) assertNull(PropertySpecs.validate(color, c), c)
    }

    @Test
    fun catalogCoversEveryNonRootTypeExactlyOnceWithDefaults() {
        val inCatalog = ElementCatalog.categories.flatMap { it.types }
        assertEquals(inCatalog.size, inCatalog.toSet().size)
        assertEquals(ElementType.entries.filter { it != ElementType.PAGE_ROOT }.toSet(), inCatalog.toSet())
        var n = 0
        val ids = IdGenerator { "x${++n}" }
        for (t in inCatalog) {
            val e = ElementCatalog.defaultElement(t, ids)
            assertEquals(t, e.type)
            assertTrue(ElementCatalog.label(t).isNotBlank())
        }
    }

    @Test
    fun specsAreGroupedUnderOrderedHeadings() {
        for (t in ElementType.entries) {
            val groups = PropertySpecs.forType(t).map { PropertySpecs.groupOf(it) }
            assertTrue(groups.all { it in PropertySpecs.groupOrder }, t.name)
            val indices = groups.map { PropertySpecs.groupOrder.indexOf(it) }
            assertEquals(indices.sorted(), indices, "${t.name}: يجب أن تتجمع الخصائص حسب المجموعات بالترتيب")
        }
    }

    @Test
    fun navMenuHasTypeLayoutItemStyleAndEffectGroups() {
        val specs = PropertySpecs.forType(ElementType.NAV_MENU)
        val byGroup = specs.groupBy { PropertySpecs.groupOf(it) }
        assertTrue(byGroup.getValue(PropertySpecs.GROUP_CONTENT).map { it.key }.containsAll(listOf("items", "active")))
        assertTrue(byGroup.getValue(PropertySpecs.GROUP_MENU_TYPE).map { it.key }.containsAll(listOf("itemStyle", "mobile", "menu.direction", "menu.justify", "menu.gap")))
        assertTrue(byGroup.getValue(PropertySpecs.GROUP_MENU_ITEMS).map { it.key }.containsAll(listOf("item.color", "item.background", "item.padding", "item.radius", "item.hoverEffect", "item.activeBackground")))
        assertTrue(byGroup.getValue(PropertySpecs.GROUP_EFFECTS).map { it.key }.containsAll(listOf("animation", "animDuration", "animDelay", "animRepeat")))
        assertTrue(byGroup.getValue(PropertySpecs.GROUP_RESPONSIVE).map { it.key }.containsAll(listOf("hideMobile", "hideDesktop")))
    }

    @Test
    fun navbarOffersStickyAndAlignmentAndEveryVisibleElementOffersEffects() {
        val navbar = PropertySpecs.forType(ElementType.NAVBAR).map { it.key }
        assertTrue(navbar.containsAll(listOf("sticky", "justify", "alignItems", "background", "padding")))
        for (t in ElementType.entries) {
            if (t == ElementType.PAGE_ROOT) continue
            val keys = PropertySpecs.forType(t).map { it.key }
            assertTrue("animation" in keys && "animDuration" in keys && "animDelay" in keys, "${t.name}: حركة الظهور")
            if (t != ElementType.NAV_MENU) assertTrue("hoverEffect" in keys && "transition" in keys, "${t.name}: تأثير المرور")
        }
    }

    @Test
    fun effectValuesAreValidated() {
        val spec = { key: String -> PropertySpecs.forType(ElementType.CARD).first { it.key == key } }
        assertNull(PropertySpecs.validate(spec("animDuration"), "0.6s"))
        assertNull(PropertySpecs.validate(spec("animDelay"), "250ms"))
        assertNotNull(PropertySpecs.validate(spec("animDuration"), "5 minutes"))
        assertNotNull(PropertySpecs.validate(spec("transition"), "1s; x"))
        assertNull(PropertySpecs.validate(spec("opacity"), "0.5"))
        assertNotNull(PropertySpecs.validate(spec("opacity"), "2"))
        assertNull(PropertySpecs.validate(spec("hoverBackground"), "#eeeeee"))
        assertNotNull(PropertySpecs.validate(spec("hoverColor"), "red; x"))
        assertNotNull(PropertySpecs.validate(spec("animation"), "spin"))
    }
}

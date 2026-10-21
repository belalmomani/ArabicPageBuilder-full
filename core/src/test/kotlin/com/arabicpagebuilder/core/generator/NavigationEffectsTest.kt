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

class NavigationEffectsTest {

    private fun doc(vararg children: Element): PageDocument {
        val empty = PageDocument.empty("ع", ids = IdGenerator { "root" })
        return empty.copy(root = empty.root.copy(children = children.toList()))
    }

    private fun el(id: String, type: ElementType, props: Map<String, String> = emptyMap(), vararg children: Element) =
        Element(id, type, props, children.toList())

    private fun gen(vararg children: Element) = HtmlGenerator.generate(doc(*children))

    private fun GeneratedSite.codes() = warnings.map { it.code }

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

    // ---- القائمة ----

    @Test
    fun navMenuRendersItemsActiveItemAndCollapseToggle() {
        val site = gen(el("m", ElementType.NAV_MENU, mapOf("items" to "الرئيسية|#\nالخدمات|about.html\nاتصل", "active" to "1")))
        val html = site.indexHtml
        assertTrue(html.contains("<nav class=\"e-m navmenu navmenu-plain navmenu-collapse\" aria-label=\"القائمة\">"))
        assertTrue(html.contains("<input type=\"checkbox\" id=\"nm-e-m\" class=\"navmenu-toggle\">"))
        assertTrue(html.contains("<label for=\"nm-e-m\" class=\"navmenu-burger\" aria-label=\"فتح القائمة\">☰</label>"))
        assertTrue(html.contains("<li><a href=\"#\" class=\"active\" aria-current=\"page\">الرئيسية</a></li>"))
        assertTrue(html.contains("<li><a href=\"about.html\">الخدمات</a></li>"))
        assertTrue(html.contains("<li><a href=\"#\">اتصل</a></li>"))
        assertTrue(site.warnings.isEmpty())
        assertBalanced(html)
    }

    @Test
    fun navMenuSubItemsExternalLinksAndInvalidLinks() {
        val site = gen(
            el(
                "m", ElementType.NAV_MENU,
                mapOf("items" to "الخدمات|https://example.com/s\n- فرعي|about.html\n- سيئ|javascript:alert(1)", "active" to "1"),
            ),
        )
        val html = site.indexHtml
        assertTrue(html.contains("<li class=\"has-sub\"><a href=\"https://example.com/s\" class=\"active\" aria-current=\"page\" rel=\"noopener noreferrer\">الخدمات</a>"))
        assertTrue(html.contains("<li><a href=\"about.html\">فرعي</a></li>"))
        assertTrue(html.contains("<li><span>سيئ</span></li>"))
        assertFalse(html.contains("javascript:"))
        assertEquals(listOf(WarningCode.INVALID_LINK), site.codes())
        assertBalanced(html)
    }

    @Test
    fun navMenuModesStylesAndInvalidValuesFallBackSafely() {
        val stack = gen(el("m", ElementType.NAV_MENU, mapOf("items" to "أ|#", "mobile" to "stack", "itemStyle" to "pills"))).indexHtml
        assertTrue(stack.contains("navmenu-pills navmenu-stack"))
        assertFalse(stack.contains("<input") || stack.contains("<label"))
        val bad = gen(el("m", ElementType.NAV_MENU, mapOf("items" to "أ|#", "mobile" to "evil\" onclick=\"x", "itemStyle" to "weird"))).indexHtml
        assertTrue(bad.contains("navmenu-plain navmenu-collapse"))
        assertFalse(bad.contains("onclick"))
        val empty = gen(el("m", ElementType.NAV_MENU, mapOf("mobile" to "none"))).indexHtml
        assertTrue(empty.contains("<ul></ul>"))
        assertBalanced(empty)
    }

    @Test
    fun navMenuTextIsEscaped() {
        val html = gen(el("m", ElementType.NAV_MENU, mapOf("items" to "<b>x</b> & y|#"))).indexHtml
        assertTrue(html.contains("&lt;b&gt;x&lt;/b&gt; &amp; y"))
        assertFalse(html.contains("<b>"))
    }

    @Test
    fun navItemStylesBecomeScopedRules() {
        val css = gen(
            el(
                "m", ElementType.NAV_MENU,
                mapOf(
                    "items" to "أ|#", "menu.justify" to "center", "menu.gap" to "8px",
                    "item.color" to "#112233", "item.padding" to "4px 8px", "item.fontSize" to "14px",
                    "item.hoverBackground" to "#eeeeee", "item.hoverEffect" to "underline",
                    "item.activeColor" to "#ffffff", "item.activeBackground" to "#1a73e8",
                ),
            ),
        ).styleCss
        assertTrue(css.contains(".e-m > ul {\n  justify-content: center;\n  gap: 8px;\n}"))
        assertTrue(css.contains(".e-m li > a, .e-m li > span {\n  color: #112233;\n  padding: 4px 8px;\n  font-size: 14px;\n}"))
        assertTrue(css.contains(".e-m li > a:hover {\n  background-color: #eeeeee;\n  box-shadow: inset 0 -2px 0 currentColor;\n}"))
        assertTrue(css.contains(".e-m li > a.active {\n  color: #ffffff;\n  background-color: #1a73e8;\n}"))
    }

    @Test
    fun invalidNavItemStylesAreRejectedWithWarnings() {
        val site = gen(el("m", ElementType.NAV_MENU, mapOf("items" to "أ|#", "item.color" to "red; } body {", "item.hoverEffect" to "spin", "menu.gap" to "-5px")))
        assertEquals(3, site.codes().count { it == WarningCode.INVALID_STYLE_VALUE })
        assertFalse(site.styleCss.contains(".e-m li"))
        assertFalse(site.styleCss.contains("body {\n"))
    }

    @Test
    fun baseCssProvidesDropdownMobileMenuAndReducedMotion() {
        val css = gen().styleCss
        assertTrue(css.contains(".navmenu .has-sub:hover > ul"))
        assertTrue(css.contains(".navmenu-collapse .navmenu-toggle:checked ~ ul"))
        assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"))
        assertTrue(css.contains("@keyframes kf-slideUp") && css.contains("@keyframes kf-pulse"))
    }

    // ---- الشريط العلوي ----

    @Test
    fun navbarIsAHeaderWithOptionalStickyClass() {
        val html = gen(
            el("n1", ElementType.NAVBAR, mapOf("sticky" to "true", "justify" to "center", "alignItems" to "flex-start"), el("t", ElementType.BOLD, mapOf("text" to "موقعي"))),
            el("n2", ElementType.NAVBAR),
        ).indexHtml
        assertTrue(html.contains("<header class=\"e-n1 navbar navbar-sticky\">"))
        assertTrue(html.contains("<header class=\"e-n2 navbar\"></header>"))
        assertBalanced(html)
    }

    @Test
    fun navbarFlexPropertiesAreEmittedAndValidated() {
        val ok = gen(el("n", ElementType.NAVBAR, mapOf("justify" to "space-around", "alignItems" to "center"))).styleCss
        assertTrue(ok.contains("justify-content: space-around;") && ok.contains("align-items: center;"))
        val site = gen(el("n", ElementType.NAVBAR, mapOf("justify" to "left; x", "alignItems" to "middle")))
        assertEquals(2, site.codes().count { it == WarningCode.INVALID_STYLE_VALUE })
    }

    // ---- التأثيرات والحركات ----

    @Test
    fun entranceAnimationBuildsShorthandWithDefaults() {
        val css = gen(
            el("a", ElementType.PARAGRAPH, mapOf("text" to "x", "animation" to "fadeIn")),
            el("b", ElementType.PARAGRAPH, mapOf("text" to "y", "animation" to "slideUp", "animDuration" to "0.8s", "animDelay" to "100ms", "animRepeat" to "2")),
            el("c", ElementType.PARAGRAPH, mapOf("text" to "z", "animation" to "pulse", "animRepeat" to "infinite")),
        ).styleCss
        assertTrue(css.contains("animation: kf-fadeIn 0.6s ease 0s 1 both;"))
        assertTrue(css.contains("animation: kf-slideUp 0.8s ease 100ms 2 both;"))
        assertTrue(css.contains("animation: kf-pulse 0.6s ease 0s infinite both;"))
    }

    @Test
    fun invalidAnimationValuesAreRejectedWithWarnings() {
        val site = gen(el("a", ElementType.PARAGRAPH, mapOf("text" to "x", "animation" to "spin; x", "animDuration" to "5 minutes", "animRepeat" to "forever")))
        assertEquals(3, site.codes().count { it == WarningCode.INVALID_STYLE_VALUE })
        assertFalse(site.styleCss.contains("animation: kf-"))
    }

    @Test
    fun hoverEffectsCreateHoverRuleAndTransition() {
        val css = gen(
            el("p", ElementType.CARD, mapOf("hoverEffect" to "lift")),
            el("q", ElementType.CARD, mapOf("hoverEffect" to "scale", "transition" to "0.5s")),
            el("r", ElementType.BUTTON, mapOf("text" to "b", "hoverBackground" to "#ffeeee", "hoverColor" to "#aa0000")),
        ).styleCss
        assertTrue(css.contains(".e-p:hover {\n  transform: translateY(-4px);\n  box-shadow: 0 8px 18px rgba(0,0,0,.2);\n}"))
        assertTrue(css.contains(".e-p {\n  transition: all 0.2s ease;\n}"))
        assertTrue(css.contains(".e-q:hover {\n  transform: scale(1.05);\n}"))
        assertTrue(css.contains(".e-q {\n  transition: all 0.5s ease;\n}"))
        assertTrue(css.contains(".e-r:hover {\n  background-color: #ffeeee;\n  color: #aa0000;\n}"))
    }

    @Test
    fun everyHoverEffectProducesADeclarationAndInvalidOnesAreRejected() {
        for (effect in CssSanitizer.hoverEffects) {
            assertTrue(CssSanitizer.convertHover(mapOf("hoverEffect" to effect)).isNotEmpty(), effect)
        }
        assertTrue(CssSanitizer.convertHover(mapOf("hoverEffect" to "explode")).isEmpty())
        val site = gen(el("p", ElementType.CARD, mapOf("hoverEffect" to "explode", "hoverBackground" to "url(x)", "transition" to "fast")))
        assertEquals(3, site.codes().count { it == WarningCode.INVALID_STYLE_VALUE })
        assertFalse(site.styleCss.contains(".e-p:hover"))
    }

    @Test
    fun opacityAcceptsZeroToOneOnly() {
        assertTrue(gen(el("p", ElementType.CARD, mapOf("opacity" to "0.5"))).styleCss.contains("opacity: 0.5;"))
        assertEquals(1, gen(el("p", ElementType.CARD, mapOf("opacity" to "2"))).codes().count { it == WarningCode.INVALID_STYLE_VALUE })
        assertEquals(1, gen(el("p", ElementType.CARD, mapOf("opacity" to "0.555"))).codes().count { it == WarningCode.INVALID_STYLE_VALUE })
    }

    // ---- أجزاء الصفحة الجاهزة ----

    private fun preset(key: String): Element {
        var n = 0
        return assertNotNull(ElementCatalog.buildPreset(key, IdGenerator { "p${++n}" }), key)
    }

    @Test
    fun navbarPresetIsAStickyHeaderWithBrandAndFullMenuWithDropdown() {
        val site = gen(preset(ElementCatalog.PRESET_NAVBAR))
        val html = site.indexHtml
        assertTrue(html.contains("navbar navbar-sticky"))
        assertTrue(html.contains("<strong class="))
        assertTrue(html.contains("navmenu-pills navmenu-collapse"))
        assertTrue(html.contains("class=\"has-sub\""))
        assertTrue(site.warnings.isEmpty())
        assertBalanced(html)
        assertTrue(site.styleCss.contains("li > a:hover"))
        assertTrue(site.styleCss.contains("li > a.active"))
    }

    @Test
    fun heroFeaturesAndFooterPresetsGenerateBalancedHtml() {
        val hero = gen(preset(ElementCatalog.PRESET_HERO))
        assertTrue(hero.indexHtml.contains("<section"))
        assertTrue(hero.styleCss.contains("animation: kf-fadeIn"))
        assertEquals(listOf(WarningCode.BUTTON_WITHOUT_ACTION), hero.codes()) // الزر بلا رابط حتى يضعه المستخدم
        assertBalanced(hero.indexHtml)

        val features = gen(preset(ElementCatalog.PRESET_FEATURES))
        assertEquals(3, Regex("<h3 ").findAll(features.indexHtml).count())
        assertTrue(features.styleCss.contains("animation: kf-slideUp 0.6s ease 150ms 1 both;"))
        assertTrue(features.warnings.isEmpty())
        assertBalanced(features.indexHtml)

        val footer = gen(preset(ElementCatalog.PRESET_FOOTER))
        assertTrue(footer.indexHtml.contains("<footer") && footer.indexHtml.contains("navmenu-stack"))
        assertTrue(footer.warnings.isEmpty())
        assertBalanced(footer.indexHtml)
    }

    @Test
    fun everyPresetInTheCatalogBuilds() {
        for (p in ElementCatalog.presets) {
            assertNotNull(ElementCatalog.buildPreset(p.key, IdGenerator { "x${System.nanoTime()}" }), p.key)
        }
    }

    @Test
    fun noJavascriptEvenInMenusAndEffects() {
        val site = gen(preset(ElementCatalog.PRESET_NAVBAR), preset(ElementCatalog.PRESET_HERO), preset(ElementCatalog.PRESET_FEATURES), preset(ElementCatalog.PRESET_FOOTER))
        assertFalse(site.indexHtml.contains("<script") || site.indexHtml.contains("javascript:") || Regex("\\son[a-z]+=").containsMatchIn(site.indexHtml))
    }
}

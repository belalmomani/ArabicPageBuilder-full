package com.arabicpagebuilder.core.generator

import com.arabicpagebuilder.core.model.Action
import com.arabicpagebuilder.core.model.ActionType
import com.arabicpagebuilder.core.model.Condition
import com.arabicpagebuilder.core.model.ConditionType
import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.IdGenerator
import com.arabicpagebuilder.core.model.Interaction
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.Trigger
import com.arabicpagebuilder.core.model.TriggerType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InteractionGeneratorTest {

    private fun doc(interactions: List<Interaction>, vararg children: Element): PageDocument {
        val empty = PageDocument.empty("ع", ids = IdGenerator { "root" })
        return empty.copy(root = empty.root.copy(children = children.toList()), interactions = interactions)
    }

    private val btn = Element("btn", ElementType.BUTTON, mapOf("text" to "تفاصيل"))
    private val details = Element("details", ElementType.SECTION, mapOf("hidden" to "true"))

    private fun showDetails(id: String = "r1") =
        Interaction(id, "إظهار", Trigger(TriggerType.CLICK, "btn"), true, emptyList(), listOf(Action(ActionType.SHOW, "details")))

    private fun rulesOf(site: GeneratedSite): JsonArray {
        val js = assertNotNull(site.mainJs)
        val m = Regex("var RULES = (\\[.*?\\]);\\n  function pick", RegexOption.DOT_MATCHES_ALL).find(js)
        return Json.parseToJsonElement(assertNotNull(m).groupValues[1]).jsonArray
    }

    @Test
    fun clickShowDetailsProducesScriptHiddenSectionAndEnabledButton() {
        val site = HtmlGenerator.generate(doc(listOf(showDetails()), btn, details))
        assertEquals(listOf("index.html", "css/style.css", "js/main.js"), site.files.keys.toList())
        assertTrue(site.indexHtml.contains("<script src=\"js/main.js\" defer></script>"))
        assertTrue(site.indexHtml.contains("<button type=\"button\" class=\"e-btn btn\">تفاصيل</button>"))
        assertTrue(site.indexHtml.contains("<section hidden class=\"e-details\"></section>"))
        assertTrue(site.warnings.isEmpty())
        val rule = rulesOf(site).single().jsonObject
        assertEquals("click", rule.getValue("on").jsonPrimitive.content)
        assertEquals(".e-btn", rule.getValue("sel").jsonPrimitive.content)
        val action = rule.getValue("do").jsonArray.single().jsonObject
        assertEquals("show", action.getValue("k").jsonPrimitive.content)
        assertEquals(".e-details", action.getValue("sel").jsonPrimitive.content)
    }

    @Test
    fun withoutInteractionsThereIsNoScriptAtAll() {
        val site = HtmlGenerator.generate(doc(emptyList(), btn, details))
        assertNull(site.mainJs)
        assertEquals(listOf("index.html", "css/style.css"), site.files.keys.toList())
        assertFalse(site.indexHtml.contains("<script"))
        // الزر بلا إجراء يبقى معطّلًا مع تحذير كما كان
        assertTrue(site.indexHtml.contains("disabled"))
        assertTrue(WarningCode.BUTTON_WITHOUT_ACTION in site.warnings.map { it.code })
        // خاصية hidden وحدها تُخفي العنصر (بلا سكربت)
        assertTrue(site.indexHtml.contains("<section hidden class=\"e-details\">"))
    }

    @Test
    fun hiddenAttributeOnlyWhenTrueAndForAnyElementType() {
        val site = HtmlGenerator.generate(
            doc(
                emptyList(),
                Element("p1", ElementType.PARAGRAPH, mapOf("text" to "أ", "hidden" to "true")),
                Element("p2", ElementType.PARAGRAPH, mapOf("text" to "ب", "hidden" to "false")),
                Element("n", ElementType.NAV_MENU, mapOf("items" to "أ|#", "hidden" to "true")),
                Element("f", ElementType.TEXT_FIELD, mapOf("label" to "س", "name" to "x", "hidden" to "true")),
            ),
        )
        assertTrue(site.indexHtml.contains("<p hidden class=\"e-p1\">أ</p>"))
        assertTrue(site.indexHtml.contains("<p class=\"e-p2\">ب</p>"))
        assertTrue(site.indexHtml.contains("<nav hidden class=\"e-n navmenu"))
        assertTrue(site.indexHtml.contains("<label hidden class=\"e-f field\">"))
        assertTrue(site.styleCss.contains("[hidden] { display: none !important; }"))
    }

    @Test
    fun invalidInteractionsAreDroppedWithWarningAndNoScriptIfNoneRemain() {
        val broken = Interaction("bad", "", Trigger(TriggerType.CLICK, "btn"), true, emptyList(), listOf(Action(ActionType.SHOW, "ghost")))
        val onlyBroken = HtmlGenerator.generate(doc(listOf(broken), btn))
        assertNull(onlyBroken.mainJs)
        val w = onlyBroken.warnings.single { it.code == WarningCode.INTERACTION_INVALID }
        assertEquals("bad", w.elementId)
        assertTrue(w.detail!!.contains("ACTION_TARGET_MISSING"))

        val mixed = HtmlGenerator.generate(doc(listOf(broken, showDetails("good")), btn, details))
        assertEquals(1, rulesOf(mixed).size)
        assertEquals(1, mixed.warnings.count { it.code == WarningCode.INTERACTION_INVALID })
    }

    @Test
    fun fieldConditionsUseInputSelectorsAndChangeEventsPerFieldType() {
        val name = Element("name", ElementType.TEXT_FIELD, mapOf("label" to "الاسم", "name" to "name"))
        val agree = Element("agree", ElementType.CHECKBOX, mapOf("label" to "أوافق", "name" to "agree"))
        val pick = Element("pick", ElementType.SELECT, mapOf("label" to "اختر", "name" to "pick", "options" to "أ\nب"))
        val err = Element("err", ElementType.PARAGRAPH, mapOf("text" to "خطأ", "hidden" to "true"))
        val rules = listOf(
            Interaction("a", "", Trigger(TriggerType.CHANGE, "name"), true, listOf(Condition(ConditionType.EMPTY, "name")), listOf(Action(ActionType.SHOW, "err"))),
            Interaction("b", "", Trigger(TriggerType.CHANGE, "agree"), true, listOf(Condition(ConditionType.CHECKED, "agree")), listOf(Action(ActionType.HIDE, "err"))),
            Interaction("c", "", Trigger(TriggerType.CHANGE, "pick"), true, emptyList(), listOf(Action(ActionType.HIDE, "err"))),
        )
        val parsed = rulesOf(HtmlGenerator.generate(doc(rules, name, agree, pick, err)))
        assertEquals(listOf("input", "change", "change"), parsed.map { it.jsonObject.getValue("on").jsonPrimitive.content })
        assertEquals(".e-name input, .e-name textarea, .e-name select", parsed[0].jsonObject.getValue("sel").jsonPrimitive.content)
        val cond = parsed[0].jsonObject.getValue("if").jsonArray.single().jsonObject
        assertEquals("empty", cond.getValue("k").jsonPrimitive.content)
        assertEquals(".e-name input, .e-name textarea, .e-name select", cond.getValue("sel").jsonPrimitive.content)
    }

    @Test
    fun submitValidationRuleWithElseAndMatchAny() {
        val form = Element("form", ElementType.FORM, mapOf("action" to "https://example.com/f"), listOf(
            Element("name", ElementType.TEXT_FIELD, mapOf("label" to "الاسم", "name" to "name")),
            Element("err", ElementType.PARAGRAPH, mapOf("text" to "مطلوب", "hidden" to "true")),
        ))
        val rule = Interaction(
            "v", "تحقق", Trigger(TriggerType.SUBMIT, "form"), matchAll = false,
            conditions = listOf(Condition(ConditionType.NOT_EMPTY, "name"), Condition(ConditionType.CONTAINS, "name", "@")),
            actions = listOf(Action(ActionType.HIDE, "err")),
            elseActions = listOf(Action(ActionType.SHOW, "err"), Action(ActionType.MESSAGE, "", "املأ الحقول")),
            preventDefault = false,
        )
        val r = rulesOf(HtmlGenerator.generate(doc(listOf(rule), form))).single().jsonObject
        assertEquals("submit", r.getValue("on").jsonPrimitive.content)
        assertEquals(".e-form", r.getValue("sel").jsonPrimitive.content)
        assertEquals("false", r.getValue("all").jsonPrimitive.content)
        assertEquals("false", r.getValue("prevent").jsonPrimitive.content)
        assertEquals(2, r.getValue("if").jsonArray.size)
        assertEquals(2, r.getValue("else").jsonArray.size)
        assertEquals("message", r.getValue("else").jsonArray[1].jsonObject.getValue("k").jsonPrimitive.content)
    }

    @Test
    fun pageLoadAndLinkTriggersWork() {
        val link = Element("l", ElementType.LINK, mapOf("text" to "اضغط"))
        val rules = listOf(
            Interaction("a", "", Trigger(TriggerType.PAGE_LOAD), true, emptyList(), listOf(Action(ActionType.SHOW, "details"))),
            Interaction("b", "", Trigger(TriggerType.CLICK, "l"), true, emptyList(), listOf(Action(ActionType.TOGGLE, "details"))),
        )
        val site = HtmlGenerator.generate(doc(rules, link, details))
        assertTrue(site.indexHtml.contains("<a class=\"e-l\" href=\"#\">اضغط</a>"))
        val parsed = rulesOf(site)
        assertEquals("load", parsed[0].jsonObject.getValue("on").jsonPrimitive.content)
        assertEquals("", parsed[0].jsonObject.getValue("sel").jsonPrimitive.content)
        assertTrue(site.warnings.isEmpty())
    }

    @Test
    fun userTextNeverBreaksOutOfTheDataAndStaysValidJson() {
        val nasty = "</script><script>alert(1)</script> \"q\" \\ & \u2028 سطر\nجديد\tتبويب مرحبا"
        val rule = Interaction("m", "", Trigger(TriggerType.CLICK, "btn"), true,
            listOf(Condition(ConditionType.EQUALS, "name", nasty)),
            listOf(Action(ActionType.MESSAGE, "", nasty), Action(ActionType.SET_TEXT, "details", nasty)))
        val name = Element("name", ElementType.TEXT_FIELD, mapOf("label" to "x", "name" to "name"))
        val site = HtmlGenerator.generate(doc(listOf(rule), btn, name, details))
        val js = assertNotNull(site.mainJs)
        assertFalse(js.contains("</script>") || js.contains("<script>"))
        assertFalse(js.contains('\u2028'))
        assertTrue(js.contains("مرحبا"))
        // البيانات تُقرأ كـ JSON صالح وتعيد النص الأصلي حرفيًا
        val r = rulesOf(site).single().jsonObject
        assertEquals(nasty, r.getValue("do").jsonArray[0].jsonObject.getValue("v").jsonPrimitive.content)
        assertEquals(nasty, r.getValue("if").jsonArray[0].jsonObject.getValue("v").jsonPrimitive.content)
        // نص المستخدم الخطير لا يظهر حرفيًا في الشيفرة (مهرَّب كله داخل البيانات)
        assertFalse(js.contains("</script><script>"))
    }

    @Test
    fun staticRuntimeHasNoDangerousApis() {
        for (bad in listOf("eval(", "innerHTML", "Function(", "document.write", "XMLHttpRequest", "fetch(", "setTimeout(", "localStorage")) {
            assertFalse(InteractionScript.RUNTIME.contains(bad), bad)
        }
        assertTrue(InteractionScript.RUNTIME.contains("textContent"))
    }

    @Test
    fun outputIsDeterministic() {
        val d = doc(listOf(showDetails()), btn, details)
        assertEquals(HtmlGenerator.generate(d), HtmlGenerator.generate(d))
    }

    @Test
    fun jsonEscapingHandlesControlAndHtmlCharacters() {
        assertEquals("\"a\\u003cb\\u003e\\u0026\\\"\\\\\\n\"", InteractionScript.str("a<b>&\"\\\n"))
        assertEquals("\"\\u0001\"", InteractionScript.str("\u0001"))
        assertEquals("\"\\u2028\\u2029\"", InteractionScript.str("\u2028\u2029"))
        assertEquals("\"عربي\"", InteractionScript.str("عربي"))
    }

    @Test
    fun deletingATargetLeavesRuleDanglingAndGeneratorFlagsIt() {
        val d = doc(listOf(showDetails()), btn) // القسم المستهدف غير موجود
        val site = HtmlGenerator.generate(d)
        assertNull(site.mainJs)
        assertTrue(WarningCode.INTERACTION_INVALID in site.warnings.map { it.code })
    }
}

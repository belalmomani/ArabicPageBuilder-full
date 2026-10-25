package com.arabicpagebuilder.core.interaction

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InteractionTextTest {

    private fun doc(): PageDocument {
        val children = listOf(
            Element("btn", ElementType.BUTTON, mapOf("text" to "تفاصيل")),
            Element("details", ElementType.SECTION, mapOf("hidden" to "true")),
            Element("form", ElementType.FORM, emptyMap(), listOf(
                Element("name", ElementType.TEXT_FIELD, mapOf("label" to "الاسم", "name" to "name")),
                Element("agree", ElementType.CHECKBOX, mapOf("label" to "أوافق", "name" to "agree")),
            )),
        )
        val empty = PageDocument.empty("ع", ids = IdGenerator { "root" })
        return empty.copy(root = empty.root.copy(children = children))
    }

    @Test
    fun everyEnumHasAnArabicName() {
        for (t in TriggerType.entries) assertTrue(InteractionText.triggerName(t).isNotBlank())
        for (t in ConditionType.entries) assertTrue(InteractionText.conditionName(t).isNotBlank())
        for (t in ActionType.entries) assertTrue(InteractionText.actionName(t).isNotBlank())
        for (p in InteractionProblem.entries) assertTrue(InteractionText.problemMessage(p).isNotBlank())
    }

    @Test
    fun summaryDescribesThreeStepsInArabic() {
        val i = Interaction(
            "r", "x", Trigger(TriggerType.CLICK, "btn"), true,
            listOf(Condition(ConditionType.NOT_EMPTY, "name")),
            listOf(Action(ActionType.SHOW, "details")),
            listOf(Action(ActionType.MESSAGE, "", "املأ الاسم")),
        )
        val (s1, s2, s3) = InteractionText.summary(doc(), i)
        assertEquals("عند الضغط على زر «تفاصيل»", s1)
        assertEquals("إذا حقل نصي «الاسم» غير فارغ", s2)
        assertEquals("أظهر قسم — وإلا: اعرض الرسالة «املأ الاسم»", s3)
    }

    @Test
    fun summaryHandlesNoConditionsNoActionsAndMissingElements() {
        val i = Interaction("r", "", Trigger(TriggerType.CLICK, "ghost"), true, emptyList(), emptyList())
        val (s1, s2, s3) = InteractionText.summary(doc(), i)
        assertTrue(s1.contains("عنصر محذوف"))
        assertEquals("بلا شروط", s2)
        assertEquals("لا إجراءات بعد", s3)
        assertEquals("غير محدد", InteractionText.elementName(doc(), ""))
        assertEquals("عند فتح الصفحة", InteractionText.describeTrigger(doc(), Trigger(TriggerType.PAGE_LOAD)))
    }

    @Test
    fun matchAnyUsesOrJoiner() {
        val i = Interaction(
            "r", "", Trigger(TriggerType.PAGE_LOAD), false,
            listOf(Condition(ConditionType.EMPTY, "name"), Condition(ConditionType.UNCHECKED, "agree")),
            listOf(Action(ActionType.HIDE, "details")),
        )
        val s2 = InteractionText.summary(doc(), i).second
        assertTrue(s2.contains(" أو "))
    }

    @Test
    fun targetFiltersMatchWhatTheValidatorAccepts() {
        val d = doc()
        assertEquals(listOf("name", "agree"), InteractionText.triggerTargets(d, TriggerType.CHANGE).map { it.id })
        assertEquals(listOf("form"), InteractionText.triggerTargets(d, TriggerType.SUBMIT).map { it.id })
        assertTrue(InteractionText.triggerTargets(d, TriggerType.PAGE_LOAD).isEmpty())
        assertEquals(listOf("agree"), InteractionText.conditionTargets(d, ConditionType.CHECKED).map { it.id })
        assertEquals(listOf("name", "agree"), InteractionText.conditionTargets(d, ConditionType.EMPTY).map { it.id })
        assertTrue(InteractionText.actionTargets(d, ActionType.SET_TEXT).none { it.type == ElementType.TEXT_FIELD })
        assertTrue(InteractionText.actionTargets(d, ActionType.MESSAGE).isEmpty())
        // كل ما يعرضه المنتقي يقبله المدقق
        for (id in InteractionText.triggerTargets(d, TriggerType.SUBMIT).map { it.id }) {
            val i = Interaction("r", "", Trigger(TriggerType.SUBMIT, id), true, emptyList(), listOf(Action(ActionType.MESSAGE, "", "x")))
            assertTrue(InteractionValidator.isValid(d, i))
        }
        for (c in ConditionType.entries) for (e in InteractionText.conditionTargets(d, c)) {
            val i = Interaction("r", "", Trigger(TriggerType.PAGE_LOAD), true, listOf(Condition(c, e.id, "v")), listOf(Action(ActionType.MESSAGE, "", "x")))
            assertTrue(InteractionValidator.isValid(d, i), "${c.name} on ${e.id}")
        }
    }

    @Test
    fun templatesPickSensibleElementsAndBecomeValidOnceCompleted() {
        val d = doc()
        val toggle = InteractionText.templateToggleOnClick(d, "t1")
        assertEquals("btn", toggle.trigger.target)
        assertEquals("details", toggle.actions.single().target)
        assertTrue(InteractionValidator.isValid(d, toggle))

        val validate = InteractionText.templateValidateBeforeSubmit(d, "t2")
        assertEquals("form", validate.trigger.target)
        assertEquals("name", validate.conditions.single().target)
        assertEquals(false, validate.preventDefault)
        // بلا إجراءات في «افعل» (يمر الإرسال) لذا يحتاج إجراءً ليصبح صالحًا
        assertNotEquals(emptyList<InteractionProblem>(), InteractionValidator.check(d, validate))
    }

    @Test
    fun templatesOnEmptyPageHaveBlankTargetsAndStayInvalid() {
        val empty = PageDocument.empty("ع", ids = IdGenerator { "root" })
        val t = InteractionText.templateToggleOnClick(empty, "t")
        assertEquals("", t.trigger.target)
        assertTrue(InteractionValidator.check(empty, t).isNotEmpty())
        assertEquals("", InteractionText.templateValidateBeforeSubmit(empty, "t").trigger.target)
    }
}

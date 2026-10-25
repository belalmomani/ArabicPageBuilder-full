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
import kotlin.test.assertTrue

class InteractionValidatorTest {

    private fun doc(): PageDocument {
        val children = listOf(
            Element("btn", ElementType.BUTTON, mapOf("text" to "تفاصيل")),
            Element("details", ElementType.SECTION, mapOf("hidden" to "true")),
            Element("form", ElementType.FORM, emptyMap(), listOf(
                Element("name", ElementType.TEXT_FIELD, mapOf("label" to "الاسم", "name" to "name")),
                Element("agree", ElementType.CHECKBOX, mapOf("label" to "أوافق", "name" to "agree")),
                Element("err", ElementType.PARAGRAPH, mapOf("text" to "الاسم مطلوب", "hidden" to "true")),
            )),
        )
        val empty = PageDocument.empty("ع", ids = IdGenerator { "root" })
        return empty.copy(root = empty.root.copy(children = children))
    }

    private fun rule(
        trigger: Trigger = Trigger(TriggerType.CLICK, "btn"),
        conditions: List<Condition> = emptyList(),
        actions: List<Action> = listOf(Action(ActionType.SHOW, "details")),
        elseActions: List<Action> = emptyList(),
    ) = Interaction("r1", "اختبار", trigger, true, conditions, actions, elseActions)

    private fun problems(i: Interaction) = InteractionValidator.check(doc(), i)

    @Test
    fun threeStepRuleWithoutConditionsIsValid() {
        assertTrue(problems(rule()).isEmpty())
        assertTrue(InteractionValidator.isValid(doc(), rule()))
    }

    @Test
    fun conditionOnFieldWithElseActionsIsValid() {
        val r = rule(
            trigger = Trigger(TriggerType.SUBMIT, "form"),
            conditions = listOf(Condition(ConditionType.NOT_EMPTY, "name")),
            actions = listOf(Action(ActionType.HIDE, "err")),
            elseActions = listOf(Action(ActionType.SHOW, "err"), Action(ActionType.MESSAGE, "", "املأ الاسم")),
        )
        assertTrue(problems(r).isEmpty())
    }

    @Test
    fun pageLoadNeedsNoTarget() {
        assertTrue(problems(rule(trigger = Trigger(TriggerType.PAGE_LOAD))).isEmpty())
    }

    @Test
    fun triggerProblems() {
        assertEquals(listOf(InteractionProblem.TRIGGER_TARGET_MISSING), problems(rule(trigger = Trigger(TriggerType.CLICK, "ghost"))))
        assertEquals(listOf(InteractionProblem.TRIGGER_TARGET_MISSING), problems(rule(trigger = Trigger(TriggerType.CLICK, ""))))
        assertEquals(listOf(InteractionProblem.TRIGGER_NOT_SUPPORTED), problems(rule(trigger = Trigger(TriggerType.SUBMIT, "btn"))))
        assertEquals(listOf(InteractionProblem.TRIGGER_NOT_SUPPORTED), problems(rule(trigger = Trigger(TriggerType.CHANGE, "btn"))))
        assertTrue(problems(rule(trigger = Trigger(TriggerType.CHANGE, "name"))).isEmpty())
    }

    @Test
    fun conditionProblems() {
        assertEquals(listOf(InteractionProblem.CONDITION_TARGET_MISSING), problems(rule(conditions = listOf(Condition(ConditionType.EMPTY, "ghost")))))
        assertEquals(listOf(InteractionProblem.CONDITION_NEEDS_FIELD), problems(rule(conditions = listOf(Condition(ConditionType.EMPTY, "btn")))))
        assertEquals(listOf(InteractionProblem.CONDITION_NEEDS_CHECKBOX), problems(rule(conditions = listOf(Condition(ConditionType.CHECKED, "name")))))
        assertEquals(listOf(InteractionProblem.CONDITION_NEEDS_VALUE), problems(rule(conditions = listOf(Condition(ConditionType.EQUALS, "name", " ")))))
        assertTrue(problems(rule(conditions = listOf(Condition(ConditionType.CHECKED, "agree")))).isEmpty())
        assertTrue(problems(rule(conditions = listOf(Condition(ConditionType.VISIBLE, "details")))).isEmpty())
        assertTrue(problems(rule(conditions = listOf(Condition(ConditionType.CONTAINS, "name", "أ")))).isEmpty())
    }

    @Test
    fun actionProblems() {
        assertEquals(listOf(InteractionProblem.NO_ACTIONS), problems(rule(actions = emptyList())))
        assertEquals(listOf(InteractionProblem.ACTION_TARGET_MISSING), problems(rule(actions = listOf(Action(ActionType.SHOW, "ghost")))))
        assertEquals(listOf(InteractionProblem.ACTION_NEEDS_VALUE), problems(rule(actions = listOf(Action(ActionType.MESSAGE, "", "")))))
        assertEquals(listOf(InteractionProblem.ACTION_NEEDS_VALUE), problems(rule(actions = listOf(Action(ActionType.SET_TEXT, "err", " ")))))
        assertEquals(listOf(InteractionProblem.ACTION_TARGET_MISSING), problems(rule(elseActions = listOf(Action(ActionType.HIDE, "ghost")))))
    }

    @Test
    fun problemsAreReportedOncePerKind() {
        val r = rule(
            trigger = Trigger(TriggerType.CLICK, "ghost"),
            conditions = listOf(Condition(ConditionType.EMPTY, "ghost"), Condition(ConditionType.EMPTY, "ghost2")),
            actions = listOf(Action(ActionType.SHOW, "g1"), Action(ActionType.SHOW, "g2")),
        )
        val p = problems(r)
        assertEquals(p.size, p.toSet().size)
        assertTrue(InteractionProblem.TRIGGER_TARGET_MISSING in p && InteractionProblem.CONDITION_TARGET_MISSING in p && InteractionProblem.ACTION_TARGET_MISSING in p)
    }
}

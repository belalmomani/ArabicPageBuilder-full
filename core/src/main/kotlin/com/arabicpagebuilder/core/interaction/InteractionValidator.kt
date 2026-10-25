package com.arabicpagebuilder.core.interaction

import com.arabicpagebuilder.core.model.Action
import com.arabicpagebuilder.core.model.ConditionType
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.Interaction
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.TriggerType
import com.arabicpagebuilder.core.model.find
import com.arabicpagebuilder.core.model.isFormField

/** سبب عدم صلاحية قاعدة تفاعل. الواجهة تحوّله إلى رسالة عربية. */
enum class InteractionProblem {
    TRIGGER_TARGET_MISSING,
    TRIGGER_NOT_SUPPORTED,
    CONDITION_TARGET_MISSING,
    CONDITION_NEEDS_FIELD,
    CONDITION_NEEDS_CHECKBOX,
    CONDITION_NEEDS_VALUE,
    ACTION_TARGET_MISSING,
    ACTION_NEEDS_VALUE,
    NO_ACTIONS,
}

/** يفحص قاعدة تفاعل مقابل المستند الحالي. القاعدة غير الصالحة لا تدخل الموقع المُصدَّر. */
object InteractionValidator {

    const val MAX_INTERACTIONS = 200
    const val MAX_STEPS = 20
    const val MAX_VALUE_LENGTH = 500
    const val MAX_NAME_LENGTH = 100

    fun check(doc: PageDocument, i: Interaction): List<InteractionProblem> {
        val problems = LinkedHashSet<InteractionProblem>()

        // الخطوة 1: عند...
        if (i.trigger.type != TriggerType.PAGE_LOAD) {
            val target = doc.find(i.trigger.target)
            when {
                target == null -> problems.add(InteractionProblem.TRIGGER_TARGET_MISSING)
                i.trigger.type == TriggerType.CHANGE && !target.type.isFormField -> problems.add(InteractionProblem.TRIGGER_NOT_SUPPORTED)
                i.trigger.type == TriggerType.SUBMIT && target.type != ElementType.FORM -> problems.add(InteractionProblem.TRIGGER_NOT_SUPPORTED)
            }
        }

        // الخطوة 2: إذا... (اختيارية)
        for (c in i.conditions) {
            val target = doc.find(c.target)
            if (target == null) {
                problems.add(InteractionProblem.CONDITION_TARGET_MISSING)
                continue
            }
            when (c.type) {
                ConditionType.EMPTY, ConditionType.NOT_EMPTY, ConditionType.EQUALS, ConditionType.CONTAINS ->
                    if (!target.type.isFormField) problems.add(InteractionProblem.CONDITION_NEEDS_FIELD)
                ConditionType.CHECKED, ConditionType.UNCHECKED ->
                    if (target.type != ElementType.CHECKBOX) problems.add(InteractionProblem.CONDITION_NEEDS_CHECKBOX)
                ConditionType.VISIBLE, ConditionType.HIDDEN -> Unit
            }
            if (c.type.needsValue && c.value.isBlank()) problems.add(InteractionProblem.CONDITION_NEEDS_VALUE)
        }

        // الخطوة 3: افعل...
        if (i.actions.isEmpty()) problems.add(InteractionProblem.NO_ACTIONS)
        for (a in i.actions + i.elseActions) checkAction(doc, a, problems)
        return problems.toList()
    }

    fun isValid(doc: PageDocument, i: Interaction): Boolean = check(doc, i).isEmpty()

    private fun checkAction(doc: PageDocument, a: Action, problems: MutableSet<InteractionProblem>) {
        if (a.type.needsTarget && doc.find(a.target) == null) problems.add(InteractionProblem.ACTION_TARGET_MISSING)
        if (a.type.needsValue && a.value.isBlank()) problems.add(InteractionProblem.ACTION_NEEDS_VALUE)
    }
}

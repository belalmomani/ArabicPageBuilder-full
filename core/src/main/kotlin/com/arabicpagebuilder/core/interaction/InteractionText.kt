package com.arabicpagebuilder.core.interaction

import com.arabicpagebuilder.core.model.Action
import com.arabicpagebuilder.core.model.ActionType
import com.arabicpagebuilder.core.model.Condition
import com.arabicpagebuilder.core.model.ConditionType
import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.Interaction
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.Trigger
import com.arabicpagebuilder.core.model.TriggerType
import com.arabicpagebuilder.core.model.find
import com.arabicpagebuilder.core.model.isFormField
import com.arabicpagebuilder.core.model.subtree
import com.arabicpagebuilder.core.session.ElementCatalog

/** النصوص العربية للبرمجة المرئية: أسماء الأنواع، وصف الخطوات الثلاث، رسائل المشكلات، وفلترة العناصر المناسبة. */
object InteractionText {

    fun triggerName(t: TriggerType): String = when (t) {
        TriggerType.CLICK -> "الضغط على عنصر"
        TriggerType.CHANGE -> "تغيّر قيمة حقل"
        TriggerType.SUBMIT -> "إرسال نموذج"
        TriggerType.PAGE_LOAD -> "فتح الصفحة"
    }

    fun conditionName(t: ConditionType): String = when (t) {
        ConditionType.EMPTY -> "الحقل فارغ"
        ConditionType.NOT_EMPTY -> "الحقل غير فارغ"
        ConditionType.EQUALS -> "الحقل يساوي"
        ConditionType.CONTAINS -> "الحقل يحتوي"
        ConditionType.CHECKED -> "المربع محدد"
        ConditionType.UNCHECKED -> "المربع غير محدد"
        ConditionType.VISIBLE -> "العنصر ظاهر"
        ConditionType.HIDDEN -> "العنصر مخفي"
    }

    fun actionName(t: ActionType): String = when (t) {
        ActionType.SHOW -> "إظهار عنصر"
        ActionType.HIDE -> "إخفاء عنصر"
        ActionType.TOGGLE -> "تبديل الظهور"
        ActionType.SCROLL_TO -> "التمرير إلى عنصر"
        ActionType.SET_TEXT -> "تغيير نص عنصر"
        ActionType.MESSAGE -> "عرض رسالة"
    }

    fun problemMessage(p: InteractionProblem): String = when (p) {
        InteractionProblem.TRIGGER_TARGET_MISSING -> "اختر العنصر الذي يبدأ التفاعل (الخطوة 1)."
        InteractionProblem.TRIGGER_NOT_SUPPORTED -> "العنصر لا يناسب نوع المحفّز (الخطوة 1)."
        InteractionProblem.CONDITION_TARGET_MISSING -> "شرط يشير إلى عنصر غير موجود (الخطوة 2)."
        InteractionProblem.CONDITION_NEEDS_FIELD -> "الشرط يحتاج حقلًا (الخطوة 2)."
        InteractionProblem.CONDITION_NEEDS_CHECKBOX -> "الشرط يحتاج مربع اختيار (الخطوة 2)."
        InteractionProblem.CONDITION_NEEDS_VALUE -> "اكتب القيمة المطلوبة في الشرط (الخطوة 2)."
        InteractionProblem.ACTION_TARGET_MISSING -> "إجراء يشير إلى عنصر غير موجود (الخطوة 3)."
        InteractionProblem.ACTION_NEEDS_VALUE -> "اكتب النص المطلوب في الإجراء (الخطوة 3)."
        InteractionProblem.NO_ACTIONS -> "أضف إجراءً واحدًا على الأقل (الخطوة 3)."
    }

    /** اسم مقروء لعنصر: «زر «تفاصيل»»، أو «عنصر محذوف». */
    fun elementName(doc: PageDocument, id: String): String {
        if (id.isBlank()) return "غير محدد"
        val e = doc.find(id) ?: return "عنصر محذوف"
        val preview = ElementCatalog.preview(e)
        val label = ElementCatalog.label(e.type)
        return if (preview.isEmpty()) label else "$label «$preview»"
    }

    fun describeTrigger(doc: PageDocument, t: Trigger): String = when (t.type) {
        TriggerType.PAGE_LOAD -> "عند فتح الصفحة"
        TriggerType.CLICK -> "عند الضغط على ${elementName(doc, t.target)}"
        TriggerType.CHANGE -> "عند تغيّر ${elementName(doc, t.target)}"
        TriggerType.SUBMIT -> "عند إرسال ${elementName(doc, t.target)}"
    }

    fun describeCondition(doc: PageDocument, c: Condition): String {
        val target = elementName(doc, c.target)
        return when (c.type) {
            ConditionType.EMPTY -> "$target فارغ"
            ConditionType.NOT_EMPTY -> "$target غير فارغ"
            ConditionType.EQUALS -> "$target يساوي «${c.value}»"
            ConditionType.CONTAINS -> "$target يحتوي «${c.value}»"
            ConditionType.CHECKED -> "$target محدد"
            ConditionType.UNCHECKED -> "$target غير محدد"
            ConditionType.VISIBLE -> "$target ظاهر"
            ConditionType.HIDDEN -> "$target مخفي"
        }
    }

    fun describeAction(doc: PageDocument, a: Action): String = when (a.type) {
        ActionType.SHOW -> "أظهر ${elementName(doc, a.target)}"
        ActionType.HIDE -> "أخفِ ${elementName(doc, a.target)}"
        ActionType.TOGGLE -> "بدّل ظهور ${elementName(doc, a.target)}"
        ActionType.SCROLL_TO -> "مرّر إلى ${elementName(doc, a.target)}"
        ActionType.SET_TEXT -> "غيّر نص ${elementName(doc, a.target)} إلى «${a.value}»"
        ActionType.MESSAGE -> "اعرض الرسالة «${a.value}»"
    }

    /** ملخص نصي من ثلاث خطوات للعرض في بطاقة التفاعل. */
    fun summary(doc: PageDocument, i: Interaction): Triple<String, String, String> {
        val step1 = describeTrigger(doc, i.trigger)
        val joiner = if (i.matchAll) " و" else " أو "
        val step2 = if (i.conditions.isEmpty()) "بلا شروط" else "إذا " + i.conditions.joinToString(joiner) { describeCondition(doc, it) }
        val step3 = buildString {
            append(if (i.actions.isEmpty()) "لا إجراءات بعد" else i.actions.joinToString("، ") { describeAction(doc, it) })
            if (i.elseActions.isNotEmpty()) append(" — وإلا: ").append(i.elseActions.joinToString("، ") { describeAction(doc, it) })
        }
        return Triple(step1, step2, step3)
    }

    // ---- العناصر المناسبة لكل اختيار (لمنتقي العناصر) ----

    private fun nonRoot(doc: PageDocument): List<Element> = doc.root.subtree().filter { it.id != doc.root.id }.toList()

    fun triggerTargets(doc: PageDocument, type: TriggerType): List<Element> = when (type) {
        TriggerType.PAGE_LOAD -> emptyList()
        TriggerType.CLICK -> nonRoot(doc)
        TriggerType.CHANGE -> nonRoot(doc).filter { it.type.isFormField }
        TriggerType.SUBMIT -> nonRoot(doc).filter { it.type == ElementType.FORM }
    }

    fun conditionTargets(doc: PageDocument, type: ConditionType): List<Element> = when (type) {
        ConditionType.EMPTY, ConditionType.NOT_EMPTY, ConditionType.EQUALS, ConditionType.CONTAINS ->
            nonRoot(doc).filter { it.type.isFormField }
        ConditionType.CHECKED, ConditionType.UNCHECKED -> nonRoot(doc).filter { it.type == ElementType.CHECKBOX }
        ConditionType.VISIBLE, ConditionType.HIDDEN -> nonRoot(doc)
    }

    fun actionTargets(doc: PageDocument, type: ActionType): List<Element> = when (type) {
        ActionType.MESSAGE -> emptyList()
        ActionType.SET_TEXT -> nonRoot(doc).filter { !it.type.isFormField }
        else -> nonRoot(doc)
    }

    // ---- قوالب جاهزة: تفاعل مبدئي بأفضل تخمين لعناصر الصفحة ----

    /** «إظهار/إخفاء عند الضغط»: أول زر أو رابط يبدّل أول عنصر مخفي (أو أول قسم). */
    fun templateToggleOnClick(doc: PageDocument, id: String, name: String = "إظهار/إخفاء عند الضغط"): Interaction {
        val trigger = nonRoot(doc).firstOrNull { it.type == ElementType.BUTTON || it.type == ElementType.LINK }
        val target = nonRoot(doc).firstOrNull { it.props["hidden"] == "true" && it.id != trigger?.id }
            ?: nonRoot(doc).firstOrNull { it.type == ElementType.SECTION && it.id != trigger?.id }
        return Interaction(
            id = id, name = name,
            trigger = Trigger(TriggerType.CLICK, trigger?.id.orEmpty()),
            actions = listOf(Action(ActionType.TOGGLE, target?.id.orEmpty())),
        )
    }

    /** «التحقق قبل الإرسال»: عند إرسال أول نموذج، إن كان أول حقل غير فارغ يمر الإرسال وإلا تظهر رسالة. */
    fun templateValidateBeforeSubmit(doc: PageDocument, id: String, name: String = "التحقق قبل الإرسال"): Interaction {
        val form = nonRoot(doc).firstOrNull { it.type == ElementType.FORM }
        val field = form?.subtree()?.firstOrNull { it.type.isFormField }
        return Interaction(
            id = id, name = name,
            trigger = Trigger(TriggerType.SUBMIT, form?.id.orEmpty()),
            conditions = listOf(Condition(ConditionType.NOT_EMPTY, field?.id.orEmpty())),
            actions = emptyList(),
            elseActions = listOf(Action(ActionType.MESSAGE, "", "يرجى ملء الحقل المطلوب")),
            preventDefault = false,
        )
    }
}

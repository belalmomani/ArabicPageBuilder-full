package com.arabicpagebuilder.core.model

/** ما يبدأ التفاعل: ضغط عنصر، تغيير قيمة حقل، إرسال نموذج، أو فتح الصفحة. */
enum class TriggerType { CLICK, CHANGE, SUBMIT, PAGE_LOAD }

/** شرط على حقل أو على ظهور عنصر. */
enum class ConditionType(val needsValue: Boolean) {
    EMPTY(false),
    NOT_EMPTY(false),
    EQUALS(true),
    CONTAINS(true),
    CHECKED(false),
    UNCHECKED(false),
    VISIBLE(false),
    HIDDEN(false),
}

/** ما يحدث: إظهار/إخفاء/تبديل عنصر، تمرير إليه، تغيير نصه، أو عرض رسالة. */
enum class ActionType(val needsTarget: Boolean, val needsValue: Boolean) {
    SHOW(true, false),
    HIDE(true, false),
    TOGGLE(true, false),
    SCROLL_TO(true, false),
    SET_TEXT(true, true),
    MESSAGE(false, true),
}

data class Trigger(val type: TriggerType, val target: String = "")

data class Condition(val type: ConditionType, val target: String = "", val value: String = "")

data class Action(val type: ActionType, val target: String = "", val value: String = "")

/**
 * قاعدة تفاعل بصرية من ثلاث خطوات: «عند» [trigger] ← «إذا» [conditions] ← «افعل» [actions]
 * (و[elseActions] عند عدم تحقق الشروط). الإشارات إلى العناصر بمعرّفاتها الثابتة.
 * [matchAll]: كل الشروط (و) أم أي شرط (أو). [preventDefault]: منع السلوك الافتراضي (مثل إرسال النموذج).
 * لا يكتب المستخدم أي كود: المولّد يبني السكربت من هذه البيانات فقط.
 */
data class Interaction(
    val id: String,
    val name: String = "",
    val trigger: Trigger,
    val matchAll: Boolean = true,
    val conditions: List<Condition> = emptyList(),
    val actions: List<Action> = emptyList(),
    val elseActions: List<Action> = emptyList(),
    val preventDefault: Boolean = true,
)

/** الحقول التي يمكن قراءة قيمها. */
val ElementType.isFormField: Boolean
    get() = this == ElementType.TEXT_FIELD || this == ElementType.EMAIL_FIELD || this == ElementType.NUMBER_FIELD ||
        this == ElementType.TEXTAREA_FIELD || this == ElementType.CHECKBOX || this == ElementType.SELECT

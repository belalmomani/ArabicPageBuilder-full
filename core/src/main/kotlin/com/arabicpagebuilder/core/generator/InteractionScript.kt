package com.arabicpagebuilder.core.generator

import com.arabicpagebuilder.core.interaction.InteractionValidator
import com.arabicpagebuilder.core.model.Action
import com.arabicpagebuilder.core.model.ActionType
import com.arabicpagebuilder.core.model.Condition
import com.arabicpagebuilder.core.model.ConditionType
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.Interaction
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.TriggerType
import com.arabicpagebuilder.core.model.find
import com.arabicpagebuilder.core.model.isFormField

/** ناتج تخطيط التفاعلات: السكربت (أو null إن لم توجد تفاعلات صالحة)، وعناصر المحفّزات بالضغط. */
data class InteractionPlan(val script: String?, val clickTriggerIds: Set<String>)

/**
 * يبني ملف js/main.js من قواعد التفاعل المرئية. **لا يدخل نص المستخدم إلى الشيفرة نفسها أبدًا**:
 * الشيفرة نص ثابت مراجَع ([RUNTIME])، وبيانات القواعد (محدِّدات CSS مشتقة من معرّفات العناصر، ونصوص الرسائل
 * والقيم) تُكتب كسلاسل JSON مُهرَّبة. السكربت لا يستخدم eval ولا innerHTML ولا مستمعات مضمّنة في HTML،
 * وتغيير النصوص يتم عبر textContent فقط.
 */
object InteractionScript {

    /** نص ثابت. العلامة /*RULES*/ يُستبدل ما بعدها بالبيانات. */
    private const val RULES_MARKER = "/*RULES*/[]"

    val RUNTIME: String = """
(function () {
  "use strict";
  var RULES = /*RULES*/[];
  function pick(sel) {
    try { return document.querySelector(sel); } catch (e) { return null; }
  }
  function val(el) {
    if (!el) { return ""; }
    if (el.type === "checkbox") { return el.checked ? "true" : ""; }
    return String(el.value == null ? "" : el.value).trim();
  }
  function holds(c) {
    var el = pick(c.sel);
    switch (c.k) {
      case "empty": return val(el) === "";
      case "notEmpty": return val(el) !== "";
      case "equals": return val(el) === c.v;
      case "contains": return val(el).indexOf(c.v) !== -1;
      case "checked": return !!el && el.checked === true;
      case "unchecked": return !el || el.checked !== true;
      case "visible": return !!el && !el.hidden;
      case "hidden": return !el || el.hidden === true;
    }
    return false;
  }
  function allow(r) {
    var i;
    if (r["if"].length === 0) { return true; }
    if (r.all) {
      for (i = 0; i < r["if"].length; i++) { if (!holds(r["if"][i])) { return false; } }
      return true;
    }
    for (i = 0; i < r["if"].length; i++) { if (holds(r["if"][i])) { return true; } }
    return false;
  }
  function act(a) {
    if (a.k === "message") { window.alert(a.v); return; }
    var el = pick(a.sel);
    if (!el) { return; }
    switch (a.k) {
      case "show": el.hidden = false; break;
      case "hide": el.hidden = true; break;
      case "toggle": el.hidden = !el.hidden; break;
      case "scroll": if (el.scrollIntoView) { el.scrollIntoView({ behavior: "smooth", block: "start" }); } break;
      case "setText": el.textContent = a.v; break;
    }
  }
  function run(r, ev) {
    var ok = allow(r);
    var list = ok ? r["do"] : r["else"];
    if (ev && ev.preventDefault && (r.prevent || (r.on === "submit" && !ok))) { ev.preventDefault(); }
    for (var i = 0; i < list.length; i++) { act(list[i]); }
  }
  function bind(r) {
    if (r.on === "load") { run(r, null); return; }
    var els = [];
    try { els = Array.prototype.slice.call(document.querySelectorAll(r.sel)); } catch (e) { els = []; }
    els.forEach(function (el) { el.addEventListener(r.on, function (ev) { run(r, ev); }); });
  }
  function start() { RULES.forEach(bind); }
  if (document.readyState === "loading") { document.addEventListener("DOMContentLoaded", start); } else { start(); }
})();
""".trimStart()

    /**
     * يفحص التفاعلات؛ الصالحة منها فقط تدخل السكربت، وغير الصالحة تُسجَّل تحذيرًا (elementId = معرّف التفاعل).
     * مفتاح البيانات: on = نوع الحدث، sel = محدِّد المحفّز، all = كل الشروط، prevent = منع الافتراضي،
     * if/do/else = الشروط والإجراءات (k = النوع، sel = المحدِّد، v = القيمة).
     */
    fun plan(doc: PageDocument, warnings: MutableList<GenerationWarning>): InteractionPlan {
        val valid = ArrayList<Interaction>()
        for (i in doc.interactions) {
            val problems = InteractionValidator.check(doc, i)
            if (problems.isEmpty()) valid.add(i) else warnings.add(GenerationWarning(i.id, WarningCode.INTERACTION_INVALID, problems.joinToString(",") { it.name }))
        }
        if (valid.isEmpty()) return InteractionPlan(null, emptySet())

        val json = valid.joinToString(",", "[", "]") { ruleJson(doc, it) }
        val clickTargets = valid.filter { it.trigger.type == TriggerType.CLICK }.map { it.trigger.target }.toSet()
        return InteractionPlan(RUNTIME.replace(RULES_MARKER, json), clickTargets)
    }

    private fun ruleJson(doc: PageDocument, i: Interaction): String {
        val on: String
        val sel: String
        when (i.trigger.type) {
            TriggerType.PAGE_LOAD -> { on = "load"; sel = "" }
            TriggerType.CLICK -> { on = "click"; sel = selector(doc, i.trigger.target) }
            TriggerType.SUBMIT -> { on = "submit"; sel = selector(doc, i.trigger.target) }
            TriggerType.CHANGE -> {
                val type = doc.find(i.trigger.target)?.type
                // حقول النص تُراقَب أثناء الكتابة (input)، وبقية الحقول عند التغيير (change) لتجنب التنفيذ المزدوج.
                on = if (type == ElementType.CHECKBOX || type == ElementType.SELECT) "change" else "input"
                sel = selector(doc, i.trigger.target)
            }
        }
        return "{\"on\":${str(on)},\"sel\":${str(sel)},\"all\":${i.matchAll},\"prevent\":${i.preventDefault}," +
            "\"if\":${i.conditions.joinToString(",", "[", "]") { conditionJson(doc, it) }}," +
            "\"do\":${i.actions.joinToString(",", "[", "]") { actionJson(doc, it) }}," +
            "\"else\":${i.elseActions.joinToString(",", "[", "]") { actionJson(doc, it) }}}"
    }

    private fun conditionJson(doc: PageDocument, c: Condition): String {
        val k = when (c.type) {
            ConditionType.EMPTY -> "empty"
            ConditionType.NOT_EMPTY -> "notEmpty"
            ConditionType.EQUALS -> "equals"
            ConditionType.CONTAINS -> "contains"
            ConditionType.CHECKED -> "checked"
            ConditionType.UNCHECKED -> "unchecked"
            ConditionType.VISIBLE -> "visible"
            ConditionType.HIDDEN -> "hidden"
        }
        return "{\"k\":${str(k)},\"sel\":${str(selector(doc, c.target))},\"v\":${str(c.value)}}"
    }

    private fun actionJson(doc: PageDocument, a: Action): String {
        val k = when (a.type) {
            ActionType.SHOW -> "show"
            ActionType.HIDE -> "hide"
            ActionType.TOGGLE -> "toggle"
            ActionType.SCROLL_TO -> "scroll"
            ActionType.SET_TEXT -> "setText"
            ActionType.MESSAGE -> "message"
        }
        val sel = if (a.type.needsTarget) selector(doc, a.target) else ""
        return "{\"k\":${str(k)},\"sel\":${str(sel)},\"v\":${str(a.value)}}"
    }

    /** محدِّد CSS للعنصر من صنفه الثابت (أحرف آمنة فقط)؛ للحقول نصل إلى عنصر الإدخال داخل الوسم. */
    internal fun selector(doc: PageDocument, id: String): String {
        val e = doc.find(id) ?: return ""
        val cls = "." + HtmlGenerator.className(e)
        return if (e.type.isFormField) "$cls input, $cls textarea, $cls select" else cls
    }

    /** سلسلة JSON مُهرَّبة؛ تهرّب أيضًا < و> و& وفواصل الأسطر الخاصة حتى لو أُدرج النص لاحقًا داخل HTML. */
    internal fun str(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c.code < 0x20 || c == '<' || c == '>' || c == '&' || c == '\u2028' || c == '\u2029' ->
                    append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> append(c)
            }
        }
        append('"')
    }
}

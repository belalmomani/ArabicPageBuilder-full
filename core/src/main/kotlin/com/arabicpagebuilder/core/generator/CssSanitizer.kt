package com.arabicpagebuilder.core.generator

/** نتيجة تحويل خصائص التنسيق: تصريحات CSS صالحة، ومفاتيح رُفضت قيمها. */
data class CssResult(val declarations: List<String>, val rejectedKeys: List<String>)

/**
 * يحوّل خصائص التنسيق في العنصر إلى CSS آمن. لا تُدرج أي قيمة إلا بعد مطابقتها
 * لنمط صارم، فلا يمكن حقن تصريحات أو قواعد إضافية عبر قيمة خاصية.
 */
object CssSanitizer {

    private val color = Regex(
        "^(#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})" +
            "|[a-zA-Z]{3,20}" +
            "|(rgb|rgba|hsl|hsla)\\(\\s*[0-9.%\\s,/]+\\))$",
    )
    private const val LEN = "(0|-?\\d+(\\.\\d+)?(px|rem|em|%|vh|vw))"
    private const val POS_LEN = "(0|\\d+(\\.\\d+)?(px|rem|em|%|vh|vw))"
    private val length = Regex("^$LEN$")
    private val positiveLength = Regex("^$POS_LEN$")

    private val fontStacks = mapOf(
        "system" to "system-ui, -apple-system, \"Segoe UI\", Tahoma, Arial, sans-serif",
        "serif" to "Georgia, \"Times New Roman\", serif",
        "mono" to "ui-monospace, Consolas, \"Courier New\", monospace",
    )
    private val shadows = mapOf(
        "none" to "none",
        "sm" to "0 1px 3px rgba(0,0,0,.2)",
        "md" to "0 4px 10px rgba(0,0,0,.2)",
        "lg" to "0 10px 25px rgba(0,0,0,.25)",
    )

    /** مفاتيح التنسيق المدعومة (الباقي يُعامل كمحتوى أو يُتجاهل). */
    val styleKeys: Set<String> = setOf(
        "color", "background", "fontSize", "fontFamily", "textAlign", "textDirection",
        "padding", "margin", "borderWidth", "borderColor", "borderStyle", "radius",
        "shadow", "width", "height", "columns", "gap",
    )

    /** خصائص تُطبَّق على الهاتف فقط (عرض أقل من 600px) وتُكتب بالبادئة m. مثل m.fontSize. */
    val mobileBaseKeys: Set<String> = setOf("fontSize", "padding", "margin", "width", "height", "columns", "gap")
    val mobileKeys: Set<String> = mobileBaseKeys.map { "m.$it" }.toSet()

    /** إخفاء العنصر على الهاتف أو على الشاشات الكبيرة (قيمتها "true"). */
    const val HIDE_MOBILE = "hideMobile"
    const val HIDE_DESKTOP = "hideDesktop"

    /** تصريحات CSS لقاعدة الهاتف: خصائص m.* الصالحة، ومعها display:none إن طُلب الإخفاء على الهاتف. */
    fun convertMobile(props: Map<String, String>): CssResult {
        val ok = ArrayList<String>()
        val bad = ArrayList<String>()
        for (key in mobileKeys) {
            val raw = props[key]?.trim() ?: continue
            val css = convertOne(key.removePrefix("m."), raw)
            if (css == null) bad.add(key) else ok.add(css)
        }
        if (props[HIDE_MOBILE]?.trim() == "true") ok.add("display: none")
        return CssResult(ok, bad)
    }

    fun hiddenOnDesktop(props: Map<String, String>): Boolean = props[HIDE_DESKTOP]?.trim() == "true"

    fun convert(props: Map<String, String>): CssResult {
        val ok = ArrayList<String>()
        val bad = ArrayList<String>()
        for (key in styleKeys) { // ترتيب ثابت لمخرجات ثابتة
            val raw = props[key]?.trim() ?: continue
            val css = convertOne(key, raw)
            if (css == null) bad.add(key) else ok.add(css)
        }
        return CssResult(ok, bad)
    }

    /** هل [value] مقبولة للمفتاح [key] (فارغ = غير صالح هنا؛ المستدعي يعامل الفارغ كحذف). */
    fun isValid(key: String, value: String): Boolean = when {
        key in styleKeys -> convertOne(key, value.trim()) != null
        key in mobileKeys -> convertOne(key.removePrefix("m."), value.trim()) != null
        else -> false
    }

    private fun convertOne(key: String, v: String): String? = when (key) {
        "color" -> if (color.matches(v)) "color: $v" else null
        "background" -> if (color.matches(v)) "background-color: $v" else null
        "borderColor" -> if (color.matches(v)) "border-color: $v" else null
        "fontSize" -> if (positiveLength.matches(v)) "font-size: $v" else null
        "fontFamily" -> fontStacks[v]?.let { "font-family: $it" }
        "textAlign" -> if (v in setOf("right", "left", "center", "justify")) "text-align: $v" else null
        "textDirection" -> if (v in setOf("rtl", "ltr")) "direction: $v" else null
        "borderStyle" -> if (v in setOf("solid", "dashed", "dotted", "none")) "border-style: $v" else null
        "borderWidth" -> if (positiveLength.matches(v)) "border-width: $v" else null
        "padding" -> boxLengths(v)?.let { "padding: $it" }
        "margin" -> boxLengths(v)?.let { "margin: $it" }
        "radius" -> boxLengths(v, allowNegative = false)?.let { "border-radius: $it" }
        "shadow" -> shadows[v]?.let { "box-shadow: $it" }
        "width" -> if (v == "auto" || positiveLength.matches(v)) "width: $v" else null
        "height" -> if (v == "auto" || positiveLength.matches(v)) "height: $v" else null
        "columns" -> if (v in setOf("1", "2", "3", "4", "5", "6")) "grid-template-columns: repeat($v, minmax(0, 1fr))" else null
        "gap" -> if (positiveLength.matches(v)) "gap: $v" else null
        else -> null
    }

    /** من 1 إلى 4 قيم أطوال مفصولة بمسافات. */
    private fun boxLengths(v: String, allowNegative: Boolean = true): String? {
        val parts = v.split(Regex("\\s+"))
        if (parts.size !in 1..4) return null
        val re = if (allowNegative) length else positiveLength
        return if (parts.all { re.matches(it) }) parts.joinToString(" ") else null
    }
}

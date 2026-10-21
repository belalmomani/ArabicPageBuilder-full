package com.arabicpagebuilder.core.generator

/** نتيجة تحويل خصائص التنسيق: تصريحات CSS صالحة، ومفاتيح رُفضت قيمها. */
data class CssResult(val declarations: List<String>, val rejectedKeys: List<String>)

/** تصريحات عناصر القائمة: القائمة نفسها (ul)، والرابط، وحالة المرور، والعنصر النشط. */
data class NavCss(
    val list: List<String>,
    val link: List<String>,
    val hover: List<String>,
    val active: List<String>,
    val rejectedKeys: List<String>,
)

/**
 * يحوّل خصائص التنسيق والتأثيرات في العنصر إلى CSS آمن. لا تُدرج أي قيمة إلا بعد مطابقتها
 * لنمط صارم أو لقائمة قيم مسموحة، فلا يمكن حقن تصريحات أو قواعد إضافية عبر قيمة خاصية.
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
    private val time = Regex("^(\\d{1,2}(\\.\\d{1,2})?s|\\d{1,5}ms)$")
    private val opacity = Regex("^(0(\\.\\d{1,2})?|1(\\.0{1,2})?|\\.\\d{1,2})$")

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

    /** قيم محاذاة flex المسموحة. */
    val justifyValues: Set<String> = setOf("flex-start", "center", "flex-end", "space-between", "space-around")
    val alignItemsValues: Set<String> = setOf("flex-start", "center", "flex-end", "stretch")
    val directionValues: Set<String> = setOf("row", "column")

    /** مفاتيح التنسيق البسيطة المدعومة (الباقي يُعامل كمحتوى أو يُتجاهل). */
    val styleKeys: Set<String> = setOf(
        "color", "background", "fontSize", "fontFamily", "textAlign", "textDirection",
        "padding", "margin", "borderWidth", "borderColor", "borderStyle", "radius",
        "shadow", "width", "height", "columns", "gap", "opacity", "justify", "alignItems", "direction",
    )

    // ---- الاستجابة للشاشات ----

    /** خصائص تُطبَّق على الهاتف فقط (عرض أقل من 600px) وتُكتب بالبادئة m. مثل m.fontSize. */
    val mobileBaseKeys: Set<String> = setOf("fontSize", "padding", "margin", "width", "height", "columns", "gap")
    val mobileKeys: Set<String> = mobileBaseKeys.map { "m.$it" }.toSet()

    /** إخفاء العنصر على الهاتف أو على الشاشات الكبيرة (قيمتها "true"). */
    const val HIDE_MOBILE = "hideMobile"
    const val HIDE_DESKTOP = "hideDesktop"

    // ---- التأثيرات والحركات (CSS فقط، بلا JavaScript) ----

    val animationNames: Set<String> = setOf("fadeIn", "slideUp", "slideDown", "slideLeft", "slideRight", "zoomIn", "bounce", "pulse")
    val repeatValues: Set<String> = setOf("1", "2", "3", "infinite")
    val hoverEffects: Set<String> = setOf("lift", "scale", "glow", "brighten", "tilt", "fade")
    val effectKeys: Set<String> = setOf(
        "animation", "animDuration", "animDelay", "animRepeat",
        "hoverEffect", "hoverBackground", "hoverColor", "transition",
    )

    // ---- القوائم (عناصر القائمة) ----

    val navHoverEffects: Set<String> = setOf("underline", "lift", "scale", "fade")
    val navKeys: Set<String> = setOf(
        "menu.justify", "menu.direction", "menu.gap",
        "item.color", "item.background", "item.padding", "item.radius", "item.fontSize",
        "item.hoverColor", "item.hoverBackground", "item.hoverEffect",
        "item.activeColor", "item.activeBackground",
    )

    /** كل المفاتيح التي يعرفها المحوّل. */
    val allKeys: Set<String> = styleKeys + mobileKeys + effectKeys + navKeys + setOf(HIDE_MOBILE, HIDE_DESKTOP)

    // ---- الاستجابة ----

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

    // ---- التحويل الرئيسي ----

    /** التنسيق الأساسي للعنصر + الحركة الدخولية + الانتقال (إن وُجد تأثير مرور). */
    fun convert(props: Map<String, String>): CssResult {
        val ok = ArrayList<String>()
        val bad = ArrayList<String>()
        for (key in styleKeys) { // ترتيب ثابت لمخرجات ثابتة
            val raw = props[key]?.trim() ?: continue
            val css = convertOne(key, raw)
            if (css == null) bad.add(key) else ok.add(css)
        }
        convertEffects(props, ok, bad)
        return CssResult(ok, bad)
    }

    private fun convertEffects(props: Map<String, String>, ok: MutableList<String>, bad: MutableList<String>) {
        fun value(key: String): String? = props[key]?.trim()?.takeIf { it.isNotEmpty() }

        // الحركة الدخولية: animation: kf-<اسم> <مدة> ease <تأخير> <تكرار> both
        val name = value("animation")
        var duration = "0.6s"
        var delay = "0s"
        var iterations = "1"
        val durationRaw = value("animDuration")
        if (durationRaw != null) {
            if (time.matches(durationRaw)) duration = durationRaw else bad.add("animDuration")
        }
        val delayRaw = value("animDelay")
        if (delayRaw != null) {
            if (time.matches(delayRaw)) delay = delayRaw else bad.add("animDelay")
        }
        val repeatRaw = value("animRepeat")
        if (repeatRaw != null) {
            if (repeatRaw in repeatValues) iterations = repeatRaw else bad.add("animRepeat")
        }
        if (name != null) {
            if (name in animationNames) ok.add("animation: kf-$name $duration ease $delay $iterations both") else bad.add("animation")
        }

        // الانتقال يلزم عند وجود أي تأثير مرور (حتى ينتقل بنعومة).
        var transitionDuration = "0.2s"
        var transitionGiven = false
        val transitionRaw = value("transition")
        if (transitionRaw != null) {
            if (time.matches(transitionRaw)) {
                transitionDuration = transitionRaw
                transitionGiven = true
            } else {
                bad.add("transition")
            }
        }
        val hover = convertHover(props)
        val effectRaw = value("hoverEffect")
        if (effectRaw != null && effectRaw !in hoverEffects) bad.add("hoverEffect")
        val hoverBgRaw = value("hoverBackground")
        if (hoverBgRaw != null && !color.matches(hoverBgRaw)) bad.add("hoverBackground")
        val hoverColorRaw = value("hoverColor")
        if (hoverColorRaw != null && !color.matches(hoverColorRaw)) bad.add("hoverColor")
        if (hover.isNotEmpty() || transitionGiven) ok.add("transition: all $transitionDuration ease")
    }

    /** تصريحات حالة المرور (:hover) للعنصر، أو قائمة فارغة. المفاتيح غير الصالحة تُتجاهل هنا وتُبلَّغ في [convert]. */
    fun convertHover(props: Map<String, String>): List<String> {
        val out = ArrayList<String>()
        when (props["hoverEffect"]?.trim()) {
            "lift" -> { out.add("transform: translateY(-4px)"); out.add("box-shadow: 0 8px 18px rgba(0,0,0,.2)") }
            "scale" -> out.add("transform: scale(1.05)")
            "glow" -> out.add("box-shadow: 0 0 14px rgba(26,115,232,.6)")
            "brighten" -> out.add("filter: brightness(1.1)")
            "tilt" -> out.add("transform: rotate(2deg)")
            "fade" -> out.add("opacity: .7")
        }
        props["hoverBackground"]?.trim()?.takeIf { color.matches(it) }?.let { out.add("background-color: $it") }
        props["hoverColor"]?.trim()?.takeIf { color.matches(it) }?.let { out.add("color: $it") }
        return out
    }

    /** تنسيق القائمة وعناصرها (المفاتيح menu.* وitem.*). */
    fun convertNav(props: Map<String, String>): NavCss {
        val list = ArrayList<String>()
        val link = ArrayList<String>()
        val hover = ArrayList<String>()
        val active = ArrayList<String>()
        val bad = ArrayList<String>()
        for (key in navKeys) {
            val raw = props[key]?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val css = convertNavOne(key, raw)
            if (css == null) {
                bad.add(key)
                continue
            }
            when (key) {
                "menu.justify", "menu.direction", "menu.gap" -> list.add(css)
                "item.hoverColor", "item.hoverBackground", "item.hoverEffect" -> hover.add(css)
                "item.activeColor", "item.activeBackground" -> active.add(css)
                else -> link.add(css)
            }
        }
        return NavCss(list, link, hover, active, bad)
    }

    private fun convertNavOne(key: String, v: String): String? = when (key) {
        "menu.justify" -> if (v in justifyValues) "justify-content: $v" else null
        "menu.direction" -> if (v in directionValues) "flex-direction: $v" else null
        "menu.gap" -> if (positiveLength.matches(v)) "gap: $v" else null
        "item.color", "item.hoverColor", "item.activeColor" -> if (color.matches(v)) "color: $v" else null
        "item.background", "item.hoverBackground", "item.activeBackground" -> if (color.matches(v)) "background-color: $v" else null
        "item.padding" -> boxLengths(v, allowNegative = false)?.let { "padding: $it" }
        "item.radius" -> boxLengths(v, allowNegative = false)?.let { "border-radius: $it" }
        "item.fontSize" -> if (positiveLength.matches(v)) "font-size: $v" else null
        "item.hoverEffect" -> when (v) {
            "underline" -> "box-shadow: inset 0 -2px 0 currentColor"
            "lift" -> "transform: translateY(-2px)"
            "scale" -> "transform: scale(1.06)"
            "fade" -> "opacity: .75"
            else -> null
        }
        else -> null
    }

    /** هل [value] مقبولة للمفتاح [key] (فارغ = غير صالح هنا؛ المستدعي يعامل الفارغ كحذف). */
    fun isValid(key: String, value: String): Boolean {
        val v = value.trim()
        return when {
            key in styleKeys -> convertOne(key, v) != null
            key in mobileKeys -> convertOne(key.removePrefix("m."), v) != null
            key in effectKeys -> validEffect(key, v)
            key in navKeys -> convertNavOne(key, v) != null
            else -> false
        }
    }

    private fun validEffect(key: String, v: String): Boolean = when (key) {
        "animation" -> v in animationNames
        "animRepeat" -> v in repeatValues
        "hoverEffect" -> v in hoverEffects
        "animDuration", "animDelay", "transition" -> time.matches(v)
        "hoverBackground", "hoverColor" -> color.matches(v)
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
        "opacity" -> if (opacity.matches(v)) "opacity: $v" else null
        "justify" -> if (v in justifyValues) "justify-content: $v" else null
        "alignItems" -> if (v in alignItemsValues) "align-items: $v" else null
        "direction" -> if (v in directionValues) "flex-direction: $v" else null
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

package com.arabicpagebuilder.core.session

import com.arabicpagebuilder.core.generator.CssSanitizer
import com.arabicpagebuilder.core.generator.UrlSanitizer
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.storage.ProjectFormat

enum class PropertyKind { TEXT, MULTILINE, URL, IMAGE_SOURCE, CHOICE, COLOR, LENGTH, BOX_LENGTHS }

data class Choice(val value: String, val label: String)

data class PropertySpec(
    val key: String,
    val label: String,
    val kind: PropertyKind,
    val choices: List<Choice> = emptyList(),
)

/** الخصائص المعروضة لكل نوع عنصر (التي تنطبق عليه فقط) وفحص قيمها. */
object PropertySpecs {

    val colorPresets: List<String> =
        listOf("#000000", "#ffffff", "#1a73e8", "#d93025", "#188038", "#f9ab00", "#5f6368", "#f1f3f4")

    private fun color(key: String, label: String) = PropertySpec(key, label, PropertyKind.COLOR)
    private fun length(key: String, label: String) = PropertySpec(key, label, PropertyKind.LENGTH)
    private fun box(key: String, label: String) = PropertySpec(key, label, PropertyKind.BOX_LENGTHS)

    private val textAlign = PropertySpec(
        "textAlign", "المحاذاة", PropertyKind.CHOICE,
        listOf(Choice("right", "يمين"), Choice("center", "وسط"), Choice("left", "يسار"), Choice("justify", "ضبط")),
    )
    private val textDirection = PropertySpec(
        "textDirection", "اتجاه النص", PropertyKind.CHOICE,
        listOf(Choice("rtl", "من اليمين"), Choice("ltr", "من اليسار")),
    )
    private val fontFamily = PropertySpec(
        "fontFamily", "نوع الخط", PropertyKind.CHOICE,
        listOf(Choice("system", "النظام"), Choice("serif", "سيريف"), Choice("mono", "أحادي")),
    )
    private val borderStyle = PropertySpec(
        "borderStyle", "نمط الحد", PropertyKind.CHOICE,
        listOf(Choice("solid", "متصل"), Choice("dashed", "متقطع"), Choice("dotted", "منقط"), Choice("none", "بلا")),
    )
    private val shadow = PropertySpec(
        "shadow", "الظل", PropertyKind.CHOICE,
        listOf(Choice("none", "بلا"), Choice("sm", "خفيف"), Choice("md", "متوسط"), Choice("lg", "كبير")),
    )

    private val containerSpecs = listOf(
        color("background", "لون الخلفية"),
        color("color", "لون النص"),
        box("padding", "المسافة الداخلية"),
        box("margin", "المسافة الخارجية"),
        box("radius", "نصف قطر الزوايا"),
        length("borderWidth", "سماكة الحد"),
        color("borderColor", "لون الحد"),
        borderStyle,
        shadow,
        length("width", "العرض"),
        length("height", "الارتفاع"),
        textAlign,
        textDirection,
    )

    fun forType(type: ElementType): List<PropertySpec> = when (type) {
        ElementType.PAGE_ROOT -> listOf(
            color("background", "لون خلفية الصفحة"),
            color("color", "لون النص"),
            fontFamily,
            textDirection,
        )
        ElementType.HEADING -> listOf(
            PropertySpec("text", "النص", PropertyKind.MULTILINE),
            PropertySpec("level", "المستوى", PropertyKind.CHOICE, listOf(Choice("1", "رئيسي"), Choice("2", "فرعي"), Choice("3", "صغير"))),
            color("color", "لون النص"),
            length("fontSize", "حجم الخط"),
            fontFamily,
            textAlign,
            textDirection,
            box("margin", "المسافة الخارجية"),
        )
        ElementType.PARAGRAPH -> listOf(
            PropertySpec("text", "النص", PropertyKind.MULTILINE),
            color("color", "لون النص"),
            length("fontSize", "حجم الخط"),
            fontFamily,
            textAlign,
            textDirection,
            box("margin", "المسافة الخارجية"),
        )
        ElementType.IMAGE -> listOf(
            PropertySpec("src", "مصدر الصورة", PropertyKind.IMAGE_SOURCE),
            PropertySpec("alt", "النص البديل", PropertyKind.TEXT),
            length("width", "العرض"),
            length("height", "الارتفاع"),
            box("radius", "نصف قطر الزوايا"),
            box("margin", "المسافة الخارجية"),
            shadow,
        )
        ElementType.LINK -> listOf(
            PropertySpec("text", "النص", PropertyKind.TEXT),
            PropertySpec("href", "الرابط", PropertyKind.URL),
            color("color", "لون النص"),
            length("fontSize", "حجم الخط"),
            fontFamily,
        )
        ElementType.BUTTON -> listOf(
            PropertySpec("text", "النص", PropertyKind.TEXT),
            PropertySpec("href", "الرابط", PropertyKind.URL),
            color("background", "لون الخلفية"),
            color("color", "لون النص"),
            length("fontSize", "حجم الخط"),
            box("radius", "نصف قطر الزوايا"),
            box("padding", "المسافة الداخلية"),
            box("margin", "المسافة الخارجية"),
            length("width", "العرض"),
        )
        ElementType.CONTAINER, ElementType.SECTION, ElementType.ROW, ElementType.COLUMN, ElementType.CARD -> containerSpecs
    }

    /** رسالة خطأ عربية إن كانت [value] غير صالحة لهذه الخاصية، وإلا null. القيمة الفارغة صالحة (تعني الحذف). */
    fun validate(spec: PropertySpec, value: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return null
        return when (spec.kind) {
            PropertyKind.TEXT, PropertyKind.MULTILINE ->
                if (value.length > ProjectFormat.MAX_PROP_VALUE_LENGTH) "النص طويل جدًا" else null
            PropertyKind.URL ->
                if (UrlSanitizer.sanitizeLink(v) == null) "رابط غير صالح. المسموح: https وhttp وmailto وtel والمراسي (#) والمسارات النسبية" else null
            PropertyKind.IMAGE_SOURCE ->
                if (UrlSanitizer.sanitizeImageSource(v) == null) "مصدر غير صالح. استخدم رابط https أو اختر صورة من الجهاز" else null
            PropertyKind.CHOICE ->
                if (spec.choices.none { it.value == v }) "قيمة غير مدعومة" else null
            PropertyKind.COLOR ->
                if (CssSanitizer.isValid(spec.key, v)) null else "لون غير صالح. مثال: #1a73e8 أو red"
            PropertyKind.LENGTH ->
                if (CssSanitizer.isValid(spec.key, v)) null else "قيمة غير صالحة. مثال: 16px أو 2rem أو 50% (وأحيانًا auto)"
            PropertyKind.BOX_LENGTHS ->
                if (CssSanitizer.isValid(spec.key, v)) null else "قيمة غير صالحة. مثال: 8px أو 8px 16px (حتى أربع قيم)"
        }
    }
}

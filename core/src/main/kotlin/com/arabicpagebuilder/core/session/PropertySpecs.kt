package com.arabicpagebuilder.core.session

import com.arabicpagebuilder.core.generator.CssSanitizer
import com.arabicpagebuilder.core.generator.PhoneSanitizer
import com.arabicpagebuilder.core.generator.UrlSanitizer
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.storage.ProjectFormat

enum class PropertyKind {
    TEXT, MULTILINE, URL, HTTPS_URL, IMAGE_SOURCE, MEDIA_SOURCE, FILE_SOURCE, PHONE, FIELD_NAME,
    CHOICE, COLOR, LENGTH, BOX_LENGTHS, TIME, OPACITY,
}

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

    private val fieldNameRegex = Regex("^[A-Za-z][A-Za-z0-9_-]{0,39}$")

    private fun text(key: String, label: String) = PropertySpec(key, label, PropertyKind.TEXT)
    private fun multiline(key: String, label: String) = PropertySpec(key, label, PropertyKind.MULTILINE)
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
    private val required = PropertySpec(
        "required", "مطلوب", PropertyKind.CHOICE,
        listOf(Choice("true", "نعم"), Choice("false", "لا")),
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

    private val buttonStyleSpecs = listOf(
        color("background", "لون الخلفية"),
        color("color", "لون النص"),
        length("fontSize", "حجم الخط"),
        box("radius", "نصف قطر الزوايا"),
        box("padding", "المسافة الداخلية"),
        box("margin", "المسافة الخارجية"),
        length("width", "العرض"),
    )

    private val inlineTextSpecs = listOf(
        color("color", "لون النص"),
        length("fontSize", "حجم الخط"),
        fontFamily,
    )

    private fun fieldSpecs(vararg extra: PropertySpec) = listOf(
        text("label", "العنوان"),
        PropertySpec("name", "اسم الحقل (حروف لاتينية)", PropertyKind.FIELD_NAME),
    ) + extra.toList() + listOf(required, box("margin", "المسافة الخارجية"), length("width", "العرض"))

    /** خصائص العنصر الأساسية ثم خصائص الاستجابة للشاشات (الهاتف/الإخفاء) حيث تنطبق. */
    fun forType(type: ElementType): List<PropertySpec> =
        (baseSpecs(type) + responsiveSpecs(type) + effectSpecs(type)).sortedBy { groupIndex(groupOf(it)) } // الترتيب ثابت داخل المجموعة

    // ---- مجموعات اللوحة: كل جزء من الخصائص تحت عنوان واضح ----
    const val GROUP_CONTENT = "المحتوى"
    const val GROUP_MENU_TYPE = "نوع القائمة وسلوكها"
    const val GROUP_MENU_ITEMS = "شكل عناصر القائمة"
    const val GROUP_STYLE = "الشكل والتنسيق"
    const val GROUP_EFFECTS = "التأثيرات والحركة"
    const val GROUP_RESPONSIVE = "الاستجابة للشاشات"

    val groupOrder: List<String> = listOf(GROUP_CONTENT, GROUP_MENU_TYPE, GROUP_MENU_ITEMS, GROUP_STYLE, GROUP_EFFECTS, GROUP_RESPONSIVE)

    private val contentKeys = setOf(
        "text", "label", "name", "placeholder", "items", "active", "cite", "src", "alt", "href", "action",
        "method", "phone", "message", "options", "level", "required",
    )

    fun groupOf(spec: PropertySpec): String = when {
        spec.key in contentKeys -> GROUP_CONTENT
        spec.key == "itemStyle" || spec.key == "mobile" || spec.key.startsWith("menu.") -> GROUP_MENU_TYPE
        spec.key.startsWith("item.") -> GROUP_MENU_ITEMS
        spec.key in CssSanitizer.effectKeys || spec.key == "opacity" -> GROUP_EFFECTS
        spec.key.startsWith("m.") || spec.key == CssSanitizer.HIDE_MOBILE || spec.key == CssSanitizer.HIDE_DESKTOP -> GROUP_RESPONSIVE
        else -> GROUP_STYLE
    }

    private fun groupIndex(group: String): Int = groupOrder.indexOf(group).let { if (it < 0) groupOrder.size else it }

    // ---- التأثيرات والحركة (لكل عنصر) ----
    private val animation = PropertySpec(
        "animation", "حركة الظهور", PropertyKind.CHOICE,
        listOf(
            Choice("fadeIn", "ظهور تدريجي"), Choice("slideUp", "انزلاق من الأسفل"), Choice("slideDown", "انزلاق من الأعلى"),
            Choice("slideRight", "انزلاق من اليسار"), Choice("slideLeft", "انزلاق من اليمين"),
            Choice("zoomIn", "تكبير"), Choice("bounce", "ارتداد"), Choice("pulse", "نبض"),
        ),
    )
    private val animRepeat = PropertySpec(
        "animRepeat", "تكرار الحركة", PropertyKind.CHOICE,
        listOf(Choice("1", "مرة"), Choice("2", "مرتان"), Choice("3", "ثلاث مرات"), Choice("infinite", "مستمر")),
    )
    private val hoverEffect = PropertySpec(
        "hoverEffect", "تأثير عند اللمس أو المرور", PropertyKind.CHOICE,
        listOf(
            Choice("lift", "رفع مع ظل"), Choice("scale", "تكبير"), Choice("glow", "توهج"),
            Choice("brighten", "إضاءة"), Choice("tilt", "إمالة"), Choice("fade", "شفافية"),
        ),
    )

    private fun animationSpecs(): List<PropertySpec> = listOf(
        animation,
        PropertySpec("animDuration", "مدة الحركة (مثل 0.6s)", PropertyKind.TIME),
        PropertySpec("animDelay", "تأخير الحركة (مثل 200ms)", PropertyKind.TIME),
        animRepeat,
    )

    private fun effectSpecs(type: ElementType): List<PropertySpec> = when (type) {
        ElementType.PAGE_ROOT -> emptyList()
        // عناصر القائمة لها تأثيرات مرورها الخاصة (شكل عناصر القائمة)؛ تبقى حركة الظهور للقائمة كلها.
        ElementType.NAV_MENU -> animationSpecs() + PropertySpec("opacity", "الشفافية (0 إلى 1)", PropertyKind.OPACITY)
        else -> animationSpecs() + listOf(
            hoverEffect,
            color("hoverBackground", "لون الخلفية عند المرور"),
            color("hoverColor", "لون النص عند المرور"),
            PropertySpec("transition", "مدة الانتقال (مثل 0.3s)", PropertyKind.TIME),
            PropertySpec("opacity", "الشفافية (0 إلى 1)", PropertyKind.OPACITY),
        )
    }

    // ---- الشريط العلوي والقوائم ----
    private val justify = PropertySpec(
        "justify", "توزيع العناصر أفقيًا", PropertyKind.CHOICE,
        listOf(Choice("flex-start", "من البداية"), Choice("center", "وسط"), Choice("flex-end", "من النهاية"), Choice("space-between", "تباعد بين"), Choice("space-around", "تباعد حول")),
    )
    private val alignItems = PropertySpec(
        "alignItems", "المحاذاة رأسيًا", PropertyKind.CHOICE,
        listOf(Choice("flex-start", "أعلى"), Choice("center", "وسط"), Choice("flex-end", "أسفل"), Choice("stretch", "تمدد")),
    )
    private val sticky = PropertySpec(
        "sticky", "تثبيت الشريط أعلى الصفحة عند التمرير", PropertyKind.CHOICE,
        listOf(Choice("true", "نعم"), Choice("false", "لا")),
    )
    private val navHover = PropertySpec(
        "item.hoverEffect", "تأثير عنصر القائمة عند المرور", PropertyKind.CHOICE,
        listOf(Choice("underline", "خط سفلي"), Choice("lift", "رفع"), Choice("scale", "تكبير"), Choice("fade", "شفافية")),
    )
    private val navSpecs = listOf(
        multiline("items", "عناصر القائمة (كل سطر: النص|الرابط — وسطر يبدأ بـ «- » عنصر فرعي)"),
        PropertySpec("active", "العنصر النشط (رقمه)", PropertyKind.CHOICE, (1..8).map { Choice(it.toString(), it.toString()) }),
        PropertySpec(
            "itemStyle", "شكل القائمة", PropertyKind.CHOICE,
            listOf(Choice("plain", "بسيط"), Choice("pills", "أقراص"), Choice("underline", "خط سفلي"), Choice("boxed", "مربعات")),
        ),
        PropertySpec(
            "mobile", "سلوك القائمة على الهاتف", PropertyKind.CHOICE,
            listOf(Choice("collapse", "زر قائمة ☰"), Choice("stack", "عمودية"), Choice("none", "كما هي")),
        ),
        PropertySpec("menu.direction", "اتجاه العناصر", PropertyKind.CHOICE, listOf(Choice("row", "أفقي"), Choice("column", "عمودي"))),
        PropertySpec(
            "menu.justify", "توزيع العناصر", PropertyKind.CHOICE,
            listOf(Choice("flex-start", "من البداية"), Choice("center", "وسط"), Choice("flex-end", "من النهاية"), Choice("space-between", "تباعد بين"), Choice("space-around", "تباعد حول")),
        ),
        length("menu.gap", "المسافة بين العناصر"),
        color("item.color", "لون نص العناصر"),
        color("item.background", "لون خلفية العناصر"),
        box("item.padding", "المسافة الداخلية للعنصر"),
        box("item.radius", "نصف قطر زوايا العنصر"),
        length("item.fontSize", "حجم خط العناصر"),
        color("item.hoverColor", "لون النص عند المرور"),
        color("item.hoverBackground", "لون الخلفية عند المرور"),
        navHover,
        color("item.activeColor", "لون نص العنصر النشط"),
        color("item.activeBackground", "لون خلفية العنصر النشط"),
        color("background", "لون خلفية القائمة"),
        box("margin", "المسافة الخارجية"),
    )

    private val hideMobile = PropertySpec(
        "hideMobile", "إخفاء على الهاتف", PropertyKind.CHOICE, listOf(Choice("true", "نعم"), Choice("false", "لا")),
    )
    private val hideDesktop = PropertySpec(
        "hideDesktop", "إخفاء على الشاشات الكبيرة", PropertyKind.CHOICE, listOf(Choice("true", "نعم"), Choice("false", "لا")),
    )

    private fun responsiveSpecs(type: ElementType): List<PropertySpec> {
        val hide = listOf(hideMobile, hideDesktop)
        return when (type) {
            ElementType.PAGE_ROOT -> emptyList()
            ElementType.HEADING, ElementType.PARAGRAPH, ElementType.TEXT, ElementType.BOLD, ElementType.ITALIC,
            ElementType.UNDERLINE, ElementType.LIST_ORDERED, ElementType.LIST_UNORDERED, ElementType.QUOTE,
            ElementType.LINK -> listOf(length("m.fontSize", "حجم الخط على الهاتف")) + hide
            ElementType.BUTTON, ElementType.DOWNLOAD_BUTTON, ElementType.CALL_BUTTON, ElementType.WHATSAPP_BUTTON,
            ElementType.SUBMIT_BUTTON -> listOf(length("m.fontSize", "حجم الخط على الهاتف"), length("m.width", "العرض على الهاتف")) + hide
            ElementType.IMAGE, ElementType.VIDEO, ElementType.AUDIO ->
                listOf(length("m.width", "العرض على الهاتف")) + hide
            ElementType.GRID -> listOf(
                PropertySpec("m.columns", "عدد الأعمدة على الهاتف", PropertyKind.CHOICE, (1..6).map { Choice(it.toString(), it.toString()) }),
                length("m.gap", "المسافة بين الأعمدة على الهاتف"),
                box("m.padding", "المسافة الداخلية على الهاتف"),
            ) + hide
            ElementType.NAV_MENU -> hide
            ElementType.CONTAINER, ElementType.SECTION, ElementType.ROW, ElementType.COLUMN, ElementType.CARD, ElementType.NAVBAR,
            ElementType.HEADER, ElementType.FOOTER, ElementType.GALLERY, ElementType.FORM -> listOf(
                box("m.padding", "المسافة الداخلية على الهاتف"),
                box("m.margin", "المسافة الخارجية على الهاتف"),
                length("m.width", "العرض على الهاتف"),
            ) + hide
            ElementType.SPACER -> listOf(length("m.height", "الارتفاع على الهاتف")) + hide
            ElementType.DIVIDER, ElementType.TEXT_FIELD, ElementType.EMAIL_FIELD, ElementType.NUMBER_FIELD,
            ElementType.TEXTAREA_FIELD, ElementType.CHECKBOX, ElementType.SELECT -> hide
        }
    }

    private fun baseSpecs(type: ElementType): List<PropertySpec> = when (type) {
        ElementType.PAGE_ROOT -> listOf(
            color("background", "لون خلفية الصفحة"),
            color("color", "لون النص"),
            fontFamily,
            textDirection,
        )
        ElementType.HEADING -> listOf(
            multiline("text", "النص"),
            PropertySpec("level", "المستوى", PropertyKind.CHOICE, listOf(Choice("1", "رئيسي"), Choice("2", "فرعي"), Choice("3", "صغير"))),
            color("color", "لون النص"),
            length("fontSize", "حجم الخط"),
            fontFamily,
            textAlign,
            textDirection,
            box("margin", "المسافة الخارجية"),
        )
        ElementType.PARAGRAPH -> listOf(
            multiline("text", "النص"),
            color("color", "لون النص"),
            length("fontSize", "حجم الخط"),
            fontFamily,
            textAlign,
            textDirection,
            box("margin", "المسافة الخارجية"),
        )
        ElementType.TEXT, ElementType.BOLD, ElementType.ITALIC, ElementType.UNDERLINE ->
            listOf(multiline("text", "النص")) + inlineTextSpecs
        ElementType.LIST_ORDERED, ElementType.LIST_UNORDERED -> listOf(
            multiline("items", "العناصر (كل سطر عنصر)"),
            color("color", "لون النص"),
            length("fontSize", "حجم الخط"),
            textAlign,
            box("margin", "المسافة الخارجية"),
        )
        ElementType.QUOTE -> listOf(
            multiline("text", "نص الاقتباس"),
            text("cite", "المصدر"),
            color("color", "لون النص"),
            length("fontSize", "حجم الخط"),
            textAlign,
            box("margin", "المسافة الخارجية"),
        )
        ElementType.DIVIDER -> listOf(color("borderColor", "اللون"), length("width", "العرض"), box("margin", "المسافة الخارجية"))
        ElementType.SPACER -> listOf(length("height", "الارتفاع"))
        ElementType.IMAGE -> listOf(
            PropertySpec("src", "مصدر الصورة", PropertyKind.IMAGE_SOURCE),
            text("alt", "النص البديل"),
            length("width", "العرض"),
            length("height", "الارتفاع"),
            box("radius", "نصف قطر الزوايا"),
            box("margin", "المسافة الخارجية"),
            shadow,
        )
        ElementType.GALLERY -> listOf(
            length("gap", "المسافة بين الصور"),
            color("background", "لون الخلفية"),
            box("padding", "المسافة الداخلية"),
            box("margin", "المسافة الخارجية"),
            box("radius", "نصف قطر الزوايا"),
        )
        ElementType.VIDEO -> listOf(
            PropertySpec("src", "ملف الفيديو أو رابطه", PropertyKind.MEDIA_SOURCE),
            length("width", "العرض"),
            length("height", "الارتفاع"),
            box("radius", "نصف قطر الزوايا"),
            box("margin", "المسافة الخارجية"),
        )
        ElementType.AUDIO -> listOf(
            PropertySpec("src", "ملف الصوت أو رابطه", PropertyKind.MEDIA_SOURCE),
            length("width", "العرض"),
            box("margin", "المسافة الخارجية"),
        )
        ElementType.LINK -> listOf(
            text("text", "النص"),
            PropertySpec("href", "الرابط", PropertyKind.URL),
            color("color", "لون النص"),
            length("fontSize", "حجم الخط"),
            fontFamily,
        )
        ElementType.BUTTON -> listOf(text("text", "النص"), PropertySpec("href", "الرابط", PropertyKind.URL)) + buttonStyleSpecs
        ElementType.DOWNLOAD_BUTTON ->
            listOf(text("text", "النص"), PropertySpec("href", "الملف أو رابطه", PropertyKind.FILE_SOURCE)) + buttonStyleSpecs
        ElementType.CALL_BUTTON ->
            listOf(text("text", "النص"), PropertySpec("phone", "رقم الهاتف", PropertyKind.PHONE)) + buttonStyleSpecs
        ElementType.WHATSAPP_BUTTON -> listOf(
            text("text", "النص"),
            PropertySpec("phone", "رقم واتساب (بصيغة دولية)", PropertyKind.PHONE),
            text("message", "رسالة جاهزة (اختياري)"),
        ) + buttonStyleSpecs
        ElementType.SUBMIT_BUTTON -> listOf(text("text", "النص")) + buttonStyleSpecs
        ElementType.FORM -> listOf(
            PropertySpec("action", "رابط استقبال النموذج (https)", PropertyKind.HTTPS_URL),
            PropertySpec("method", "طريقة الإرسال", PropertyKind.CHOICE, listOf(Choice("post", "POST"), Choice("get", "GET"))),
            color("background", "لون الخلفية"),
            box("padding", "المسافة الداخلية"),
            box("margin", "المسافة الخارجية"),
            box("radius", "نصف قطر الزوايا"),
            length("width", "العرض"),
        )
        ElementType.TEXT_FIELD, ElementType.EMAIL_FIELD, ElementType.NUMBER_FIELD ->
            fieldSpecs(text("placeholder", "نص إرشادي"))
        ElementType.TEXTAREA_FIELD -> fieldSpecs(text("placeholder", "نص إرشادي"))
        ElementType.CHECKBOX -> fieldSpecs()
        ElementType.SELECT -> fieldSpecs(multiline("options", "الخيارات (كل سطر خيار)"))
        ElementType.GRID -> listOf(
            PropertySpec("columns", "عدد الأعمدة", PropertyKind.CHOICE, (1..6).map { Choice(it.toString(), it.toString()) }),
            length("gap", "المسافة بين الأعمدة"),
        ) + containerSpecs
        ElementType.NAVBAR -> listOf(sticky, justify, alignItems) + containerSpecs
        ElementType.NAV_MENU -> navSpecs
        ElementType.ROW -> containerSpecs + listOf(justify, alignItems, length("gap", "المسافة بين الأعمدة"))
        ElementType.CONTAINER, ElementType.SECTION, ElementType.COLUMN, ElementType.CARD,
        ElementType.HEADER, ElementType.FOOTER -> containerSpecs
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
            PropertyKind.HTTPS_URL ->
                if (UrlSanitizer.sanitizeLink(v)?.startsWith("https://", ignoreCase = true) != true) "يلزم رابط يبدأ بـ https://" else null
            PropertyKind.IMAGE_SOURCE ->
                if (UrlSanitizer.sanitizeImageSource(v) == null) "مصدر غير صالح. استخدم رابط https أو اختر صورة من الجهاز" else null
            PropertyKind.MEDIA_SOURCE ->
                if (UrlSanitizer.sanitizeImageSource(v) == null) "مصدر غير صالح. استخدم رابط https أو اختر ملفًا من الجهاز" else null
            PropertyKind.FILE_SOURCE ->
                if (UrlSanitizer.sanitizeLink(v) == null) "مسار أو رابط غير صالح. اختر ملفًا من الجهاز أو استخدم رابط https" else null
            PropertyKind.PHONE ->
                if (PhoneSanitizer.normalize(v) == null) "رقم غير صالح. مثال: +962790000000 (من 5 إلى 15 رقمًا)" else null
            PropertyKind.FIELD_NAME ->
                if (!fieldNameRegex.matches(v)) "اسم غير صالح. يبدأ بحرف لاتيني ثم حروف أو أرقام أو _ أو - (حتى 40 حرفًا)" else null
            PropertyKind.CHOICE ->
                if (spec.choices.none { it.value == v }) "قيمة غير مدعومة" else null
            PropertyKind.COLOR ->
                if (CssSanitizer.isValid(spec.key, v)) null else "لون غير صالح. مثال: #1a73e8 أو red"
            PropertyKind.LENGTH ->
                if (CssSanitizer.isValid(spec.key, v)) null else "قيمة غير صالحة. مثال: 16px أو 2rem أو 50% (وأحيانًا auto)"
            PropertyKind.TIME ->
                if (CssSanitizer.isValid(spec.key, v)) null else "مدة غير صالحة. مثال: 0.6s أو 300ms (حتى 99 ثانية)"
            PropertyKind.OPACITY ->
                if (CssSanitizer.isValid(spec.key, v)) null else "قيمة غير صالحة. رقم من 0 إلى 1 مثل 0.5"
            PropertyKind.BOX_LENGTHS ->
                if (CssSanitizer.isValid(spec.key, v)) null else "قيمة غير صالحة. مثال: 8px أو 8px 16px (حتى أربع قيم)"
        }
    }
}

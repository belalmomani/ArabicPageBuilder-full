package com.arabicpagebuilder.core.session

import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.IdGenerator

data class ElementCategory(val title: String, val types: List<ElementType>)

/** عنصر جاهز مركّب من عدة عناصر (مثل نموذج التواصل). */
data class Preset(val key: String, val title: String)

/** مكتبة العناصر وتسمياتها العربية وقيمها الافتراضية والعناصر الجاهزة. */
object ElementCatalog {

    val categories: List<ElementCategory> = listOf(
        ElementCategory("التنقل", listOf(ElementType.NAVBAR, ElementType.NAV_MENU)),
        ElementCategory(
            "النصوص",
            listOf(
                ElementType.HEADING, ElementType.PARAGRAPH, ElementType.TEXT, ElementType.BOLD, ElementType.ITALIC,
                ElementType.UNDERLINE, ElementType.LIST_ORDERED, ElementType.LIST_UNORDERED, ElementType.QUOTE, ElementType.DIVIDER,
            ),
        ),
        ElementCategory("الصور والوسائط", listOf(ElementType.IMAGE, ElementType.GALLERY, ElementType.VIDEO, ElementType.AUDIO)),
        ElementCategory(
            "الروابط والأزرار",
            listOf(
                ElementType.LINK, ElementType.BUTTON, ElementType.DOWNLOAD_BUTTON, ElementType.CALL_BUTTON,
                ElementType.WHATSAPP_BUTTON, ElementType.SUBMIT_BUTTON,
            ),
        ),
        ElementCategory(
            "التخطيط",
            listOf(
                ElementType.HEADER, ElementType.FOOTER, ElementType.SECTION, ElementType.CONTAINER, ElementType.ROW,
                ElementType.COLUMN, ElementType.GRID, ElementType.CARD, ElementType.SPACER,
            ),
        ),
        ElementCategory(
            "النماذج",
            listOf(
                ElementType.FORM, ElementType.TEXT_FIELD, ElementType.EMAIL_FIELD, ElementType.NUMBER_FIELD,
                ElementType.TEXTAREA_FIELD, ElementType.CHECKBOX, ElementType.SELECT,
            ),
        ),
    )

    const val PRESET_CONTACT_FORM = "contact_form"

    const val PRESET_NAVBAR = "navbar"
    const val PRESET_HERO = "hero"
    const val PRESET_FEATURES = "features"
    const val PRESET_FOOTER = "footer"

    val presets: List<Preset> = listOf(
        Preset(PRESET_NAVBAR, "شريط علوي مع قائمة (شعار + 4 روابط + قائمة فرعية)"),
        Preset(PRESET_HERO, "قسم رئيسي (عنوان + نص + زر)"),
        Preset(PRESET_FEATURES, "شبكة مزايا (3 بطاقات)"),
        Preset(PRESET_CONTACT_FORM, "نموذج تواصل (اسم، بريد، رسالة، إرسال)"),
        Preset(PRESET_FOOTER, "تذييل الصفحة (حقوق + روابط)"),
    )

    fun label(type: ElementType): String = when (type) {
        ElementType.PAGE_ROOT -> "الصفحة"
        ElementType.CONTAINER -> "حاوية"
        ElementType.SECTION -> "قسم"
        ElementType.ROW -> "صف"
        ElementType.COLUMN -> "عمود"
        ElementType.CARD -> "بطاقة محتوى"
        ElementType.NAVBAR -> "شريط علوي"
        ElementType.NAV_MENU -> "قائمة تنقل"
        ElementType.HEADER -> "رأس الصفحة"
        ElementType.FOOTER -> "تذييل الصفحة"
        ElementType.GRID -> "شبكة أعمدة"
        ElementType.GALLERY -> "معرض صور"
        ElementType.SPACER -> "مسافة"
        ElementType.HEADING -> "عنوان"
        ElementType.PARAGRAPH -> "فقرة"
        ElementType.TEXT -> "نص عادي"
        ElementType.BOLD -> "نص عريض"
        ElementType.ITALIC -> "نص مائل"
        ElementType.UNDERLINE -> "نص تحته خط"
        ElementType.LIST_ORDERED -> "قائمة مرتبة"
        ElementType.LIST_UNORDERED -> "قائمة غير مرتبة"
        ElementType.QUOTE -> "اقتباس"
        ElementType.DIVIDER -> "فاصل"
        ElementType.IMAGE -> "صورة"
        ElementType.VIDEO -> "فيديو"
        ElementType.AUDIO -> "صوت"
        ElementType.LINK -> "رابط نصي"
        ElementType.BUTTON -> "زر"
        ElementType.DOWNLOAD_BUTTON -> "زر تنزيل"
        ElementType.CALL_BUTTON -> "زر اتصال"
        ElementType.WHATSAPP_BUTTON -> "زر واتساب"
        ElementType.SUBMIT_BUTTON -> "زر إرسال نموذج"
        ElementType.FORM -> "نموذج"
        ElementType.TEXT_FIELD -> "حقل نصي"
        ElementType.EMAIL_FIELD -> "حقل بريد إلكتروني"
        ElementType.NUMBER_FIELD -> "حقل رقم"
        ElementType.TEXTAREA_FIELD -> "حقل نص طويل"
        ElementType.CHECKBOX -> "مربع اختيار"
        ElementType.SELECT -> "قائمة خيارات"
    }

    /** عنصر جديد بمعرّف جديد وقيم افتراضية مفيدة. */
    fun defaultElement(type: ElementType, ids: IdGenerator): Element {
        require(type != ElementType.PAGE_ROOT) { "لا يمكن إنشاء جذر جديد" }
        val props: Map<String, String> = when (type) {
            ElementType.HEADING -> mapOf("text" to "عنوان رئيسي", "level" to "1")
            ElementType.PARAGRAPH -> mapOf("text" to "اكتب فقرتك هنا")
            ElementType.TEXT -> mapOf("text" to "نص عادي")
            ElementType.BOLD -> mapOf("text" to "نص عريض")
            ElementType.ITALIC -> mapOf("text" to "نص مائل")
            ElementType.UNDERLINE -> mapOf("text" to "نص تحته خط")
            ElementType.LIST_ORDERED, ElementType.LIST_UNORDERED -> mapOf("items" to "العنصر الأول\nالعنصر الثاني\nالعنصر الثالث")
            ElementType.QUOTE -> mapOf("text" to "اكتب الاقتباس هنا")
            ElementType.SPACER -> mapOf("height" to "24px")
            ElementType.NAVBAR -> mapOf("justify" to "space-between", "alignItems" to "center", "padding" to "12px 16px")
            ElementType.NAV_MENU -> mapOf(
                "items" to "الرئيسية|#\nالخدمات|#\nمن نحن|#\nاتصل بنا|#",
                "active" to "1",
                "itemStyle" to "plain",
                "mobile" to "collapse",
            )
            ElementType.GRID -> mapOf("columns" to "3", "gap" to "12px")
            ElementType.LINK -> mapOf("text" to "رابط")
            ElementType.BUTTON -> mapOf("text" to "زر")
            ElementType.DOWNLOAD_BUTTON -> mapOf("text" to "تنزيل الملف")
            ElementType.CALL_BUTTON -> mapOf("text" to "اتصل بنا")
            ElementType.WHATSAPP_BUTTON -> mapOf("text" to "تواصل عبر واتساب")
            ElementType.SUBMIT_BUTTON -> mapOf("text" to "إرسال")
            ElementType.TEXT_FIELD -> mapOf("label" to "الاسم", "name" to "name")
            ElementType.EMAIL_FIELD -> mapOf("label" to "البريد الإلكتروني", "name" to "email")
            ElementType.NUMBER_FIELD -> mapOf("label" to "الرقم", "name" to "number")
            ElementType.TEXTAREA_FIELD -> mapOf("label" to "الرسالة", "name" to "message")
            ElementType.CHECKBOX -> mapOf("label" to "أوافق على الشروط", "name" to "agree")
            ElementType.SELECT -> mapOf("label" to "اختر", "name" to "choice", "options" to "الخيار الأول\nالخيار الثاني")
            else -> emptyMap()
        }
        return Element(ids.next(), type, props)
    }

    /** يبني العنصر الجاهز [key] (شجرة كاملة بمعرّفات جديدة)، أو null إن لم يوجد. */
    fun buildPreset(key: String, ids: IdGenerator): Element? = when (key) {
        PRESET_NAVBAR -> {
            val brand = Element(ids.next(), ElementType.BOLD, mapOf("text" to "اسم الموقع", "fontSize" to "20px"))
            val menu = Element(
                ids.next(), ElementType.NAV_MENU,
                mapOf(
                    "items" to "الرئيسية|#\nالخدمات|#\n- خدمة أولى|#\n- خدمة ثانية|#\nمن نحن|#\nاتصل بنا|#",
                    "active" to "1",
                    "itemStyle" to "pills",
                    "mobile" to "collapse",
                    "item.hoverBackground" to "#e8f0fe",
                    "item.hoverEffect" to "lift",
                    "item.activeBackground" to "#1a73e8",
                    "item.activeColor" to "#ffffff",
                ),
            )
            Element(
                ids.next(), ElementType.NAVBAR,
                mapOf("justify" to "space-between", "alignItems" to "center", "padding" to "12px 16px", "background" to "#ffffff", "shadow" to "sm", "sticky" to "true"),
                listOf(brand, menu),
            )
        }
        PRESET_HERO -> Element(
            ids.next(), ElementType.SECTION,
            mapOf("background" to "#f1f3f4", "padding" to "56px 16px", "textAlign" to "center", "animation" to "fadeIn"),
            listOf(
                Element(ids.next(), ElementType.HEADING, mapOf("text" to "عنوان رئيسي جذاب", "level" to "1")),
                Element(ids.next(), ElementType.PARAGRAPH, mapOf("text" to "وصف قصير يشرح ما تقدمه وما الذي يميّزك.")),
                Element(ids.next(), ElementType.BUTTON, mapOf("text" to "ابدأ الآن", "hoverEffect" to "lift", "radius" to "8px")),
            ),
        )
        PRESET_FEATURES -> Element(
            ids.next(), ElementType.GRID,
            mapOf("columns" to "3", "gap" to "16px", "padding" to "24px 16px"),
            (1..3).map { n ->
                Element(
                    ids.next(), ElementType.CARD,
                    mapOf("hoverEffect" to "lift", "animation" to "slideUp", "animDelay" to "${(n - 1) * 150}ms"),
                    listOf(
                        Element(ids.next(), ElementType.HEADING, mapOf("text" to "ميزة $n", "level" to "3")),
                        Element(ids.next(), ElementType.PARAGRAPH, mapOf("text" to "وصف مختصر للميزة.")),
                    ),
                )
            },
        )
        PRESET_FOOTER -> Element(
            ids.next(), ElementType.FOOTER,
            mapOf("background" to "#202124", "color" to "#ffffff", "padding" to "24px 16px", "textAlign" to "center"),
            listOf(
                Element(ids.next(), ElementType.TEXT, mapOf("text" to "© جميع الحقوق محفوظة")),
                Element(
                    ids.next(), ElementType.NAV_MENU,
                    mapOf(
                        "items" to "سياسة الخصوصية|#\nالشروط|#\nتواصل معنا|#",
                        "itemStyle" to "plain",
                        "mobile" to "stack",
                        "menu.justify" to "center",
                        "item.hoverEffect" to "underline",
                    ),
                ),
            ),
        )
        PRESET_CONTACT_FORM -> {
            val form = Element(ids.next(), ElementType.FORM)
            val children = listOf(
                Element(ids.next(), ElementType.TEXT_FIELD, mapOf("label" to "الاسم", "name" to "name", "required" to "true")),
                Element(ids.next(), ElementType.EMAIL_FIELD, mapOf("label" to "البريد الإلكتروني", "name" to "email", "required" to "true")),
                Element(ids.next(), ElementType.TEXTAREA_FIELD, mapOf("label" to "الرسالة", "name" to "message", "required" to "true")),
                Element(ids.next(), ElementType.SUBMIT_BUTTON, mapOf("text" to "إرسال")),
            )
            form.copy(children = children)
        }
        else -> null
    }

    /** نص قصير يصف العنصر في قائمة البنية. */
    fun preview(e: Element): String {
        if (e.type == ElementType.NAV_MENU) {
            val count = e.props["items"].orEmpty().lines().count { it.isNotBlank() && !it.trim().startsWith("- ") }
            return "$count عناصر"
        }
        val raw = e.props["text"] ?: e.props["label"] ?: e.props["items"] ?: e.props["alt"] ?: e.props["src"] ?: e.props["phone"] ?: return ""
        val oneLine = raw.replace('\n', ' ').trim()
        return if (oneLine.length > 40) oneLine.take(40) + "…" else oneLine
    }
}

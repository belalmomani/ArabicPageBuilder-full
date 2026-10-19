package com.arabicpagebuilder.core.session

import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.IdGenerator

data class ElementCategory(val title: String, val types: List<ElementType>)

/** مكتبة العناصر المنفّذة فعلًا (تتوسع في المرحلة 10)، وتسمياتها العربية وقيمها الافتراضية. */
object ElementCatalog {

    val categories: List<ElementCategory> = listOf(
        ElementCategory("النصوص", listOf(ElementType.HEADING, ElementType.PARAGRAPH)),
        ElementCategory("الصور والوسائط", listOf(ElementType.IMAGE)),
        ElementCategory("الروابط والأزرار", listOf(ElementType.LINK, ElementType.BUTTON)),
        ElementCategory(
            "التخطيط",
            listOf(ElementType.SECTION, ElementType.CONTAINER, ElementType.ROW, ElementType.COLUMN, ElementType.CARD),
        ),
    )

    fun label(type: ElementType): String = when (type) {
        ElementType.PAGE_ROOT -> "الصفحة"
        ElementType.CONTAINER -> "حاوية"
        ElementType.SECTION -> "قسم"
        ElementType.ROW -> "صف"
        ElementType.COLUMN -> "عمود"
        ElementType.CARD -> "بطاقة محتوى"
        ElementType.HEADING -> "عنوان"
        ElementType.PARAGRAPH -> "فقرة"
        ElementType.IMAGE -> "صورة"
        ElementType.LINK -> "رابط نصي"
        ElementType.BUTTON -> "زر"
    }

    /** عنصر جديد بمعرّف جديد وقيم افتراضية مفيدة. */
    fun defaultElement(type: ElementType, ids: IdGenerator): Element {
        require(type != ElementType.PAGE_ROOT) { "لا يمكن إنشاء جذر جديد" }
        val props: Map<String, String> = when (type) {
            ElementType.HEADING -> mapOf("text" to "عنوان رئيسي", "level" to "1")
            ElementType.PARAGRAPH -> mapOf("text" to "اكتب فقرتك هنا")
            ElementType.LINK -> mapOf("text" to "رابط")
            ElementType.BUTTON -> mapOf("text" to "زر")
            else -> emptyMap()
        }
        return Element(ids.next(), type, props)
    }

    /** نص قصير يصف العنصر في قائمة البنية. */
    fun preview(e: Element): String {
        val raw = e.props["text"] ?: e.props["alt"] ?: e.props["src"] ?: return ""
        val oneLine = raw.replace('\n', ' ').trim()
        return if (oneLine.length > 40) oneLine.take(40) + "…" else oneLine
    }
}

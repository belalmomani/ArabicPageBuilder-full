package com.arabicpagebuilder.core.model

enum class TextDirection { RTL, LTR }

/** نموذج الصفحة: المصدر الأساسي للحقيقة. HTML يُولَّد منه ولا يُقرأ منه. */
data class PageDocument(
    val title: String,
    val direction: TextDirection,
    val root: Element,
) {
    init {
        require(root.type == ElementType.PAGE_ROOT) { "جذر الصفحة يجب أن يكون من نوع PAGE_ROOT" }
    }

    fun find(id: String): Element? = root.find(id)

    companion object {
        fun empty(
            title: String,
            direction: TextDirection = TextDirection.RTL,
            ids: IdGenerator = UuidIdGenerator,
        ): PageDocument = PageDocument(title, direction, Element(ids.next(), ElementType.PAGE_ROOT))
    }
}

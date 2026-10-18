package com.arabicpagebuilder.core.model

/**
 * أنواع العناصر. هذه المجموعة الأولية تكفي لقواعد التداخل؛
 * بقية أنواع المكتبة تُضاف في المرحلة 10 مع خصائصها.
 */
enum class ElementType(val acceptsChildren: Boolean) {
    PAGE_ROOT(true),
    CONTAINER(true),
    SECTION(true),
    ROW(true),
    COLUMN(true),
    CARD(true),
    HEADING(false),
    PARAGRAPH(false),
    IMAGE(false),
    LINK(false),
    BUTTON(false),
}

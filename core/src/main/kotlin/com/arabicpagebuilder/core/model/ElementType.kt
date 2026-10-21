package com.arabicpagebuilder.core.model

/**
 * أنواع العناصر. [acceptsChildren] يحدد من يستطيع احتواء عناصر أخرى.
 * أسماء الأنواع جزء من صيغة ملف المشروع؛ لا تُعاد تسميتها.
 */
enum class ElementType(val acceptsChildren: Boolean) {
    PAGE_ROOT(true),

    // التخطيط
    CONTAINER(true),
    SECTION(true),
    ROW(true),
    COLUMN(true),
    CARD(true),
    HEADER(true),
    FOOTER(true),
    GRID(true),
    GALLERY(true),
    SPACER(false),

    // النصوص
    HEADING(false),
    PARAGRAPH(false),
    TEXT(false),
    BOLD(false),
    ITALIC(false),
    UNDERLINE(false),
    LIST_ORDERED(false),
    LIST_UNORDERED(false),
    QUOTE(false),
    DIVIDER(false),

    // الوسائط
    IMAGE(false),
    VIDEO(false),
    AUDIO(false),

    // الروابط والأزرار
    LINK(false),
    BUTTON(false),
    DOWNLOAD_BUTTON(false),
    CALL_BUTTON(false),
    WHATSAPP_BUTTON(false),
    SUBMIT_BUTTON(false),

    // النماذج
    FORM(true),
    TEXT_FIELD(false),
    EMAIL_FIELD(false),
    NUMBER_FIELD(false),
    TEXTAREA_FIELD(false),
    CHECKBOX(false),
    SELECT(false),
}

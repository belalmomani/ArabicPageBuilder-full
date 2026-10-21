package com.arabicpagebuilder.core.generator

enum class WarningCode {
    INVALID_LINK,
    INVALID_IMAGE_SOURCE,
    MISSING_IMAGE_SOURCE,
    MISSING_ALT,
    INVALID_STYLE_VALUE,
    BUTTON_WITHOUT_ACTION,
    MISSING_MEDIA_SOURCE,
    INVALID_MEDIA_SOURCE,
    INVALID_PHONE,
    SUBMIT_OUTSIDE_FORM,
    FORM_WITHOUT_ACTION,
    INVALID_FORM_ACTION,
    INVALID_FIELD_NAME,
}

data class GenerationWarning(val elementId: String, val code: WarningCode, val detail: String? = null)

/**
 * ناتج التوليد: ملفات الموقع (مسار نسبي → محتوى) وتحذيرات عن قيم رُفضت.
 * لا يُنشأ main.js إلا عند وجود حاجة فعلية، ولا حاجة له في هذه المرحلة.
 */
data class GeneratedSite(
    val files: Map<String, String>,
    val warnings: List<GenerationWarning>,
) {
    val indexHtml: String get() = files.getValue(HtmlGenerator.INDEX_PATH)
    val styleCss: String get() = files.getValue(HtmlGenerator.CSS_PATH)
}

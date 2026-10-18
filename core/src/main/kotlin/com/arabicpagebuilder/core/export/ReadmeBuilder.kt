package com.arabicpagebuilder.core.export

import com.arabicpagebuilder.core.generator.GenerationWarning
import com.arabicpagebuilder.core.generator.WarningCode

/** يبني README.txt (بالعربية) يشرح التشغيل والقيود والتنبيهات. */
internal object ReadmeBuilder {

    fun build(title: String, exportWarnings: List<ExportWarning>, generationWarnings: List<GenerationWarning>): String =
        buildString {
            val cleanTitle = title.replace(Regex("\\s+"), " ").trim().ifEmpty { "بلا عنوان" }
            appendLine("موقع أُنشئ بواسطة منشئ الصفحات العربي")
            appendLine("==========================================")
            appendLine("عنوان الصفحة: $cleanTitle")
            appendLine()
            appendLine("طريقة التشغيل")
            appendLine("1. فك ضغط هذا الملف.")
            appendLine("2. افتح index.html في أي متصفح.")
            appendLine("3. للنشر على الإنترنت ارفع محتويات المجلد كما هي إلى أي استضافة ويب.")
            appendLine()
            appendLine("محتويات المجلد")
            appendLine("- index.html : الصفحة")
            appendLine("- css/style.css : التنسيق")
            appendLine("- assets/ : الصور والملفات المستخدمة في الصفحة (إن وُجدت)")
            appendLine()
            appendLine("قيود")
            appendLine("- الصور والروابط الخارجية تحتاج اتصالًا بالإنترنت لتظهر.")
            appendLine("- الموقع ثابت (HTML وCSS فقط) ولا يتضمن خادمًا؛ ميزات مثل إرسال النماذج أو الخدمات الخارجية تحتاج خادمًا أو إعدادًا إضافيًا.")

            val missingAssets = exportWarnings.filter { it.code == ExportWarningCode.MISSING_ASSET }
            val missingTargets = exportWarnings.filter { it.code == ExportWarningCode.LOCAL_LINK_TARGET_MISSING }
            if (missingAssets.isNotEmpty() || missingTargets.isNotEmpty() || generationWarnings.isNotEmpty()) {
                appendLine()
                appendLine("تنبيهات أثناء التصدير")
                for (w in missingAssets) appendLine("- ملف صورة مفقود ولم يُضمَّن: ${w.path}")
                for (w in missingTargets) appendLine("- رابط داخلي إلى ملف غير موجود في المشروع: ${w.path}")
                for ((code, count) in generationWarnings.groupingBy { it.code }.eachCount()) {
                    appendLine("- ${label(code)} (×$count)")
                }
            }
        }

    private fun label(code: WarningCode): String = when (code) {
        WarningCode.INVALID_LINK -> "رابط غير صالح حُذف"
        WarningCode.INVALID_IMAGE_SOURCE -> "مصدر صورة غير صالح فحُذفت الصورة"
        WarningCode.MISSING_IMAGE_SOURCE -> "صورة بلا مصدر فحُذفت"
        WarningCode.MISSING_ALT -> "صورة بلا نص بديل"
        WarningCode.INVALID_STYLE_VALUE -> "قيمة تنسيق غير صالحة فتم تجاهلها"
        WarningCode.BUTTON_WITHOUT_ACTION -> "زر بلا إجراء (يظهر معطّلًا)"
    }
}

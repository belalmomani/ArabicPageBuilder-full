package com.arabicpagebuilder.ui

import com.arabicpagebuilder.core.edit.EditError
import com.arabicpagebuilder.core.export.ExportError
import com.arabicpagebuilder.core.export.ExportWarning
import com.arabicpagebuilder.core.export.ExportWarningCode
import com.arabicpagebuilder.core.generator.WarningCode
import com.arabicpagebuilder.core.storage.StoreError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** كل رسائل الأخطاء والتحذيرات بالعربية في مكان واحد. */
object Messages {

    fun editError(e: EditError): String = when (e) {
        EditError.ELEMENT_NOT_FOUND -> "اختر عنصرًا أولًا."
        EditError.PARENT_NOT_FOUND -> "تعذر العثور على الحاوية."
        EditError.CANNOT_MODIFY_ROOT -> "لا يمكن تعديل الصفحة نفسها بهذا الإجراء."
        EditError.CANNOT_ADD_ROOT_TYPE -> "لا يمكن إضافة صفحة داخل صفحة."
        EditError.PARENT_CANNOT_HAVE_CHILDREN -> "هذا العنصر لا يقبل عناصر بداخله."
        EditError.CANNOT_MOVE_INTO_SELF -> "لا يمكن نقل العنصر إلى نفسه."
        EditError.CANNOT_MOVE_INTO_DESCENDANT -> "لا يمكن نقل العنصر إلى داخل أحد عناصره."
        EditError.INDEX_OUT_OF_RANGE -> "موضع الإدراج غير صالح."
        EditError.DUPLICATE_ID -> "تعذر الإجراء بسبب تكرار معرّف عنصر."
        EditError.INVALID_PROPERTY_KEY -> "اسم الخاصية غير صالح."
        EditError.ALREADY_FIRST -> "العنصر في أول الترتيب بالفعل."
        EditError.ALREADY_LAST -> "العنصر في آخر الترتيب بالفعل."
    }

    fun storeError(e: StoreError, detail: String? = null): String = when (e) {
        StoreError.INVALID_ID -> "معرّف المشروع غير صالح."
        StoreError.INVALID_NAME -> "اسم غير صالح (يجب ألا يكون فارغًا ولا أطول من 200 حرف)."
        StoreError.NOT_FOUND -> "المشروع غير موجود."
        StoreError.ALREADY_EXISTS -> "المشروع موجود بالفعل."
        StoreError.CORRUPT -> "ملف المشروع تالف."
        StoreError.NO_BACKUP -> "لا توجد نسخة احتياطية لهذا المشروع."
        StoreError.ASSET_NOT_ALLOWED -> "نوع الملف غير مدعوم (المدعوم: png وjpg وjpeg وgif وwebp)."
        StoreError.ASSET_TOO_LARGE -> "الملف أكبر من الحد المسموح."
        StoreError.INVALID_ARCHIVE -> "الملف ليس مشروعًا صالحًا${detail.suffix()}"
        StoreError.ARCHIVE_TOO_LARGE -> "الملف أكبر من الحد المسموح للاستيراد."
        StoreError.IO_ERROR -> "تعذر الوصول إلى التخزين${detail.suffix()}"
    }

    private fun String?.suffix(): String = if (this.isNullOrBlank()) "." else ": $this"

    fun warning(code: WarningCode): String = when (code) {
        WarningCode.INVALID_LINK -> "رابط غير صالح (حُذف من الناتج)"
        WarningCode.INVALID_IMAGE_SOURCE -> "مصدر صورة غير صالح (حُذفت الصورة)"
        WarningCode.MISSING_IMAGE_SOURCE -> "صورة بلا مصدر (لا تظهر)"
        WarningCode.MISSING_ALT -> "صورة بلا نص بديل"
        WarningCode.INVALID_STYLE_VALUE -> "قيمة تنسيق غير صالحة (تُجوهلت)"
        WarningCode.BUTTON_WITHOUT_ACTION -> "زر بلا رابط (يظهر معطّلًا)"
        WarningCode.MISSING_MEDIA_SOURCE -> "فيديو أو صوت بلا مصدر (لا يظهر)"
        WarningCode.INVALID_MEDIA_SOURCE -> "مصدر وسائط غير صالح (حُذف العنصر)"
        WarningCode.INVALID_PHONE -> "رقم هاتف مفقود أو غير صالح (يظهر الزر معطّلًا)"
        WarningCode.SUBMIT_OUTSIDE_FORM -> "زر إرسال خارج أي نموذج (يظهر معطّلًا)"
        WarningCode.FORM_WITHOUT_ACTION -> "نموذج بلا رابط استقبال: لن تُرسَل بياناته قبل ربطه بخادم أو خدمة خارجية"
        WarningCode.INVALID_FORM_ACTION -> "رابط استقبال النموذج غير صالح (يلزم https)"
        WarningCode.INVALID_FIELD_NAME -> "حقل بلا اسم صالح (لن تُرسَل قيمته)"
    }

    fun exportError(e: ExportError, detail: String?): String = when (e) {
        ExportError.ASSET_TOO_LARGE -> "ملف كبير جدًا داخل المشروع${detail.suffix2()}"
        ExportError.TOTAL_TOO_LARGE -> "حجم الموقع الكلي أكبر من الحد المسموح."
        ExportError.IO_ERROR -> "تعذرت الكتابة (ربما المساحة ممتلئة)${detail.suffix2()}"
        ExportError.VERIFICATION_FAILED -> "فشل التحقق من الملف الناتج${detail.suffix2()}"
    }

    private fun String?.suffix2(): String = if (this.isNullOrBlank()) "." else ": $this"

    fun exportWarning(w: ExportWarning): String = when (w.code) {
        ExportWarningCode.MISSING_ASSET -> "ملف مفقود لم يُضمَّن: ${w.path}"
        ExportWarningCode.LOCAL_LINK_TARGET_MISSING -> "رابط داخلي إلى ملف غير موجود: ${w.path}"
    }

    fun formatDate(millis: Long): String =
        if (millis <= 0L) "—" else SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(millis))
}

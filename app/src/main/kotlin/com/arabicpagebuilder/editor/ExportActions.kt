package com.arabicpagebuilder.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.arabicpagebuilder.core.export.AssetSource
import com.arabicpagebuilder.core.export.ExportResult
import com.arabicpagebuilder.core.export.SiteExporter
import com.arabicpagebuilder.ui.Messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** عمليات التصدير والمشاركة. الملفات الوسيطة تُكتب في cache/exports وتُحذف بعد النسخ. */
object ExportActions {

    private object NoAssets : AssetSource {
        override fun exists(path: String): Boolean = false
        override fun open(path: String): InputStream? = null
    }

    fun exportsDir(context: Context): File = File(context.cacheDir, "exports").also { it.mkdirs() }

    /** اسم ملف آمن من اسم المشروع (يبقي الحروف العربية). */
    fun safeFileName(name: String, fallback: String = "site"): String {
        val cleaned = name.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("").replace(Regex("_+"), "_").trim('_').take(60)
        return cleaned.ifEmpty { fallback }
    }

    /** يبني ZIP الموقع في [target] مع التحقق منه، دون المساس بأي ملف موجود عند الفشل. */
    suspend fun buildSiteZip(state: EditorState, target: File): ExportResult = withContext(Dispatchers.IO) {
        SiteExporter.exportToFile(state.doc, state.assetSource() ?: NoAssets, target)
    }

    /** ينسخ [file] إلى [uri] (اختاره المستخدم عبر منتقي ملفات أندرويد). يُرجع رسالة خطأ أو null عند النجاح. */
    suspend fun copyToUri(context: Context, file: File, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext "تعذر فتح الملف الوجهة."
            out.use { o -> file.inputStream().use { it.copyTo(o) } }
            null
        } catch (e: Exception) {
            "تعذرت الكتابة إلى الوجهة: ${e.message}"
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "مشاركة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** نص نتيجة التصدير للمستخدم: نجاح مع التحذيرات، أو سبب الفشل. */
    fun describe(result: ExportResult): String = when (result) {
        is ExportResult.Failure -> "فشل التصدير: " + Messages.exportError(result.error, result.detail) + "\nمشروعك الأصلي لم يتغير."
        is ExportResult.Success -> buildString {
            append("تم تصدير الموقع (${result.entries.size} ملفًا).")
            val lines = result.warnings.map { Messages.exportWarning(it) } + result.generationWarnings.map { Messages.warning(it.code) }.distinct()
            if (lines.isNotEmpty()) {
                append("\n\nتنبيهات:")
                for (l in lines) append("\n• ").append(l)
            }
            append("\n\nافتح index.html بعد فك الضغط. النماذج تحتاج خادمًا أو خدمة خارجية لاستقبال البيانات.")
        }
    }

    /** يحذف ملفات التصدير المؤقتة الأقدم من يوم. */
    fun cleanOldExports(context: Context) {
        val limit = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        exportsDir(context).listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
    }
}

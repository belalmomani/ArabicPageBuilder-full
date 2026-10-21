package com.arabicpagebuilder.core.storage

/** تنظيف أسماء الملفات المستوردة وتحديد نوعها. SVG مستبعد عمدًا لأنه قد يحمل سكربتات. */
object AssetNames {
    val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp")
    val MEDIA_EXT = setOf("mp4", "webm", "mp3", "ogg", "wav", "m4a")
    val FILE_EXT = setOf("pdf", "txt", "csv", "docx", "xlsx", "pptx", "zip")

    /** مجلد الأصول المناسب للامتداد (images/media/files) أو null إن لم يكن مسموحًا. */
    fun folderFor(ext: String): String? = when (ext) {
        in IMAGE_EXT -> "images"
        in MEDIA_EXT -> "media"
        in FILE_EXT -> "files"
        else -> null
    }

    data class Parsed(val base: String, val ext: String)

    /** null إن كان الامتداد غير مسموح أو الاسم بلا امتداد. */
    fun parse(original: String): Parsed? {
        val name = original.substringAfterLast('/').substringAfterLast('\\').trim()
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return null
        val ext = name.substring(dot + 1).lowercase()
        if (folderFor(ext) == null) return null
        var base = name.substring(0, dot)
            .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(60)
        if (base.isEmpty()) base = "file"
        return Parsed(base, ext)
    }

    /** اسم ملف كما يُخزَّن داخل مشروع أو أرشيف: أحرف آمنة، وامتداد صغير مسموح يطابق المجلد (images/media). */
    fun isValidStoredName(name: String, folder: String): Boolean {
        if (name.length > 100) return false
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return false
        val ext = name.substring(dot + 1)
        val allowed = when (folder) {
            "images" -> IMAGE_EXT
            "media" -> MEDIA_EXT
            "files" -> FILE_EXT
            else -> return false
        }
        if (ext !in allowed) return false
        return name.substring(0, dot).all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
}

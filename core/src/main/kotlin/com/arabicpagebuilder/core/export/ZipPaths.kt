package com.arabicpagebuilder.core.export

/** قواعد أسماء المدخلات داخل ZIP (تمنع Zip Slip والمسارات المطلقة). */
object ZipPaths {

    fun isSafeEntryName(name: String): Boolean {
        if (name.isEmpty() || name.length > 512) return false
        if (name.startsWith("/") || name.contains('\\') || name.contains(':')) return false
        if (name.any { it.isISOControl() }) return false
        val body = name.removeSuffix("/")
        if (body.isEmpty()) return false
        return body.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }
}

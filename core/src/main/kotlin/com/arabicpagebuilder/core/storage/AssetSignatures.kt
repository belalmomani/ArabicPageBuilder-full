package com.arabicpagebuilder.core.storage

/** فحص أن محتوى ملف الصورة يطابق امتداده (بالبايتات الأولى). لا يغني عن فحص كامل، لكنه يرفض الملفات المنتحلة. */
object AssetSignatures {
    const val HEADER_BYTES = 12

    /** true/false للامتدادات الصورية المعروفة؛ null إن لم يكن الامتداد مما نفحصه. */
    fun matches(ext: String, header: ByteArray): Boolean? = when (ext.lowercase()) {
        "png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        "jpg", "jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF)
        "gif" -> startsWith(header, 'G'.code, 'I'.code, 'F'.code, '8'.code, '7'.code, 'a'.code) ||
            startsWith(header, 'G'.code, 'I'.code, 'F'.code, '8'.code, '9'.code, 'a'.code)
        "webp" -> header.size >= 12 &&
            startsWith(header, 'R'.code, 'I'.code, 'F'.code, 'F'.code) &&
            header[8].toInt() == 'W'.code && header[9].toInt() == 'E'.code &&
            header[10].toInt() == 'B'.code && header[11].toInt() == 'P'.code
        else -> null
    }

    private fun startsWith(header: ByteArray, vararg expected: Int): Boolean {
        if (header.size < expected.size) return false
        for (i in expected.indices) if ((header[i].toInt() and 0xFF) != expected[i]) return false
        return true
    }
}

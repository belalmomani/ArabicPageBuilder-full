package com.arabicpagebuilder.core.generator

/**
 * فحص الروابط ومصادر الصور. يُرجع القيمة كما هي إن كانت مقبولة، وإلا null.
 * المسموح: http وhttps وmailto وtel والمراسي (#) والمسارات النسبية الآمنة.
 * المرفوض: javascript: وdata: وغيرها، والمسارات المطلقة و"..".
 */
object UrlSanitizer {

    private val webUrl = Regex("^https?://[^/?#\\s]+\\S*$", RegexOption.IGNORE_CASE)

    fun sanitizeLink(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty() || hasUnsafeChars(v)) return null
        if (v.startsWith("#")) return if (v.length > 1) v else null
        val scheme = schemeOf(v)
        if (scheme != null) {
            return when (scheme) {
                "http", "https" -> if (webUrl.matches(v)) v else null
                "mailto", "tel" -> if (v.length > scheme.length + 1) v else null
                else -> null
            }
        }
        return sanitizeLocalPath(v)
    }

    fun sanitizeImageSource(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty() || hasUnsafeChars(v)) return null
        val scheme = schemeOf(v)
        if (scheme != null) {
            return if ((scheme == "http" || scheme == "https") && webUrl.matches(v)) v else null
        }
        return sanitizeLocalPath(v)
    }

    /** مسار نسبي داخل الموقع المصدَّر فقط. */
    fun sanitizeLocalPath(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty() || hasUnsafeChars(v)) return null
        if (v.startsWith("/") || v.contains('\\') || v.contains(':')) return null
        val pathPart = v.substringBefore('?').substringBefore('#')
        if (pathPart.isEmpty()) return null
        if (pathPart.split('/').any { it == ".." }) return null
        return v
    }

    private fun hasUnsafeChars(v: String): Boolean = v.any { it.isISOControl() || it.isWhitespace() }

    /** اسم المخطط إن وُجد قبل أي / أو ? أو #. */
    private fun schemeOf(v: String): String? {
        val colon = v.indexOf(':')
        if (colon <= 0) return null
        val firstDelimiter = v.indexOfAny(charArrayOf('/', '?', '#'))
        if (firstDelimiter != -1 && firstDelimiter < colon) return null
        return v.substring(0, colon).lowercase()
    }
}

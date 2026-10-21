package com.arabicpagebuilder.core.generator

/** تنظيف أرقام الهواتف وترميز نص الرسائل لروابط tel: وwa.me. */
object PhoneSanitizer {

    /**
     * يُرجع الرقم بأرقام لاتينية فقط (مع + اختياري في البداية)، أو null إن كان غير صالح.
     * المسموح داخله: أرقام ومسافات وشرطات وأقواس. الطول من 5 إلى 15 رقمًا.
     */
    fun normalize(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val plus = t.startsWith("+")
        val body = if (plus) t.substring(1) else t
        if (!body.all { it in '0'..'9' || it == ' ' || it == '-' || it == '(' || it == ')' }) return null
        val digits = body.filter { it in '0'..'9' }
        if (digits.length !in 5..15) return null
        return (if (plus) "+" else "") + digits
    }

    /** رقم واتساب الدولي بلا + (wa.me تتطلب أرقامًا فقط). */
    fun whatsappNumber(raw: String): String? = normalize(raw)?.removePrefix("+")
}

object PercentEncoder {
    private const val HEX = "0123456789ABCDEF"

    /** ترميز UTF-8 بنمط النسبة المئوية؛ يبقي الأحرف غير المحجوزة فقط. */
    fun encode(s: String): String = buildString {
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (c < 128 && (ch.isLetterOrDigit() || ch == '-' || ch == '.' || ch == '_' || ch == '~')) {
                append(ch)
            } else {
                append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
            }
        }
    }
}

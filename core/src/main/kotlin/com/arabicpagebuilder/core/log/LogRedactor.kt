package com.arabicpagebuilder.core.log

/**
 * يحجب من نصوص السجل ما قد يكشف خصوصية المستخدم قبل حفظها أو مشاركتها:
 * المسارات المطلقة على الجهاز، والبريد الإلكتروني، والمعرّفات الطويلة (UUID).
 * محتوى المشاريع لا يدخل السجل أصلًا؛ هذا خط دفاع ثانٍ ضد نصوص الاستثناءات.
 */
object LogRedactor {
    private val path = Regex("(?<![\\w:])/(?:data|storage|sdcard|mnt|home|Users|var|tmp|proc|system)(?:/[^\\s:;,\"'()\\[\\]<>]*)*")
    private val email = Regex("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+")
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    fun redact(text: String): String =
        text.replace(email, "<بريد>").replace(uuid, "<معرّف>").replace(path, "<مسار>")
}

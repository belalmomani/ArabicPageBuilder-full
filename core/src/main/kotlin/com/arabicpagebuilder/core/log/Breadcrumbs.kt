package com.arabicpagebuilder.core.log

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * آخر خطوات المستخدم (أسماء شاشات وإجراءات عامة مثل «تصدير ZIP»)، تُرفق بتقرير العطل لتوضح ما سبقه.
 * لا تحتوي أسماء مشاريع ولا نصوصًا أدخلها المستخدم. سعة ثابتة؛ الأقدم يُسقَط.
 */
class Breadcrumbs(private val max: Int = 30, private val clock: () -> Long = { System.currentTimeMillis() }) {
    private val items = ArrayDeque<Pair<Long, String>>()

    @Synchronized
    fun add(text: String) {
        items.addLast(clock() to LogRedactor.redact(text).take(120))
        while (items.size > max) items.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<String> {
        val f = SimpleDateFormat("HH:mm:ss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return items.map { (t, s) -> "${f.format(Date(t))} $s" }
    }
}

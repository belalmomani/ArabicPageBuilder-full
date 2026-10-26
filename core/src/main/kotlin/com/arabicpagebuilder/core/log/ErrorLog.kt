package com.arabicpagebuilder.core.log

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

enum class LogKind { CRASH, ERROR, WARNING }

data class LogEntry(val id: String, val time: Long, val kind: LogKind, val title: String)

/**
 * سجل أخطاء محلي: ملف نصي صغير لكل حدث داخل مجلد التطبيق الخاص، بلا أي إرسال تلقائي.
 * المستخدم هو من يشاركه يدويًا. لا يرمي استثناءات أبدًا (فشل التسجيل لا يجوز أن يسبب عطلًا آخر).
 * سعة محدودة (عدد الأحداث وحجم كل حدث)، وكل نص يمر بـ [LogRedactor].
 */
class ErrorLog(
    private val dir: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxEntries: Int = 50,
    private val maxChars: Int = 60_000,
) {
    private val seq = AtomicInteger(0)

    companion object {
        private val ID = Regex("^(\\d{13})-(\\d{3})-(CRASH|ERROR|WARNING)$")
        private const val SEEN_FILE = ".seen"
        const val MAX_TITLE = 200
    }

    /** يسجّل حدثًا. [header] أزواج وصفية (إصدار، جهاز...). يُرجع الإدخال أو null إن تعذرت الكتابة. */
    @Synchronized
    fun record(
        kind: LogKind,
        title: String,
        throwable: Throwable? = null,
        header: Map<String, String> = emptyMap(),
        breadcrumbs: List<String> = emptyList(),
    ): LogEntry? {
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) return null
            val now = clock()
            val id = String.format(Locale.ROOT, "%013d-%03d-%s", now, seq.getAndIncrement() % 1000, kind.name)
            val cleanTitle = LogRedactor.redact(title).replace('\n', ' ').take(MAX_TITLE)
            val body = buildString {
                append("time: ").append(formatTime(now)).append('\n')
                append("kind: ").append(kind.name).append('\n')
                append("title: ").append(cleanTitle).append('\n')
                for ((k, v) in header) append(k.replace('\n', ' ')).append(": ").append(LogRedactor.redact(v).replace('\n', ' ')).append('\n')
                if (throwable != null) {
                    append("--- stack ---\n")
                    append(LogRedactor.redact(throwable.stackTraceToString())).append('\n')
                }
                if (breadcrumbs.isNotEmpty()) {
                    append("--- breadcrumbs ---\n")
                    for (b in breadcrumbs) append("- ").append(LogRedactor.redact(b)).append('\n')
                }
            }
            val capped = if (body.length > maxChars) body.take(maxChars) + "\n… (قُطع النص لطوله)\n" else body
            File(dir, "$id.log").writeText(capped, Charsets.UTF_8)
            trim()
            LogEntry(id, now, kind, cleanTitle)
        } catch (e: Throwable) {
            null
        }
    }

    /** الأحداث من الأحدث إلى الأقدم. */
    @Synchronized
    fun list(): List<LogEntry> = files().mapNotNull { f ->
        val m = ID.matchEntire(f.nameWithoutExtension) ?: return@mapNotNull null
        LogEntry(f.nameWithoutExtension, m.groupValues[1].toLong(), LogKind.valueOf(m.groupValues[3]), readTitle(f))
    }

    /** نص حدث واحد، أو null إن كان المعرّف غير صالح أو غير موجود (يمنع الخروج من المجلد). */
    @Synchronized
    fun read(id: String): String? {
        if (!ID.matches(id)) return null
        val f = File(dir, "$id.log")
        return try {
            if (f.isFile) f.readText(Charsets.UTF_8) else null
        } catch (e: Exception) {
            null
        }
    }

    /** يحذف كل الأحداث. يُرجع عددها. */
    @Synchronized
    fun clear(): Int {
        val all = files()
        var n = 0
        for (f in all) if (f.delete()) n++
        return n
    }

    /**
     * تقرير نصي واحد للمشاركة: ترويسة ثم الأحداث من الأحدث، ويُقتطع الأقدم إن تجاوز [maxReportChars].
     */
    @Synchronized
    fun report(header: Map<String, String> = emptyMap(), maxReportChars: Int = 400_000): String {
        val head = buildString {
            append("تقرير أخطاء — منشئ الصفحات العربي\n")
            append("وقت التقرير: ").append(formatTime(clock())).append('\n')
            for ((k, v) in header) append(k).append(": ").append(LogRedactor.redact(v).replace('\n', ' ')).append('\n')
            append("لا يحتوي هذا التقرير على محتوى مشاريعك. تتم مشاركته يدويًا فقط.\n")
        }
        val out = StringBuilder(head)
        var included = 0
        val all = list()
        for (e in all) {
            val text = read(e.id) ?: continue
            if (out.length + text.length + 40 > maxReportChars) break
            out.append("\n==================== ").append(e.id).append(" ====================\n").append(text)
            included++
        }
        if (included < all.size) out.append("\n… (حُذفت ${all.size - included} أحداث أقدم لطول التقرير)\n")
        return out.toString()
    }

    /** عدد أعطال التطبيق (CRASH) التي لم يطّلع عليها المستخدم بعد. */
    @Synchronized
    fun unseenCrashes(): Int {
        val seen = try {
            File(dir, SEEN_FILE).takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
        return list().count { it.kind == LogKind.CRASH && it.time > seen }
    }

    @Synchronized
    fun markSeen() {
        try {
            if (dir.isDirectory || dir.mkdirs()) File(dir, SEEN_FILE).writeText(clock().toString())
        } catch (e: Exception) {
            // تجاهل: لا يجوز أن يفشل التطبيق بسبب السجل
        }
    }

    // ---- تفاصيل ----

    private fun files(): List<File> =
        (dir.listFiles { f -> f.isFile && f.name.endsWith(".log") } ?: emptyArray()).sortedByDescending { it.name }

    private fun trim() {
        val all = files()
        if (all.size > maxEntries) for (f in all.drop(maxEntries)) f.delete()
    }

    private fun readTitle(f: File): String = try {
        f.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.take(4).firstOrNull { it.startsWith("title: ") }?.removePrefix("title: ") ?: "" }
    } catch (e: Exception) {
        ""
    }

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))
}

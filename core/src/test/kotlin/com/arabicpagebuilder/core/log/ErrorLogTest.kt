package com.arabicpagebuilder.core.log

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ErrorLogTest {

    private var tick = 1_700_000_000_000L
    private fun newLog(dir: File, max: Int = 50, chars: Int = 60_000) = ErrorLog(File(dir, "logs"), clock = { ++tick }, maxEntries = max, maxChars = chars)

    private fun <T> withDir(block: (File) -> T): T {
        val dir = Files.createTempDirectory("apb-log").toFile()
        try {
            return block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun recordsCrashWithStackHeaderAndBreadcrumbs() = withDir { dir ->
        val log = newLog(dir)
        val e = log.record(
            LogKind.CRASH, "java.lang.IllegalStateException: boom", IllegalStateException("boom", RuntimeException("السبب")),
            header = mapOf("version" to "0.7.0", "device" to "Pixel 7, Android 14"),
            breadcrumbs = listOf("12:00:01 شاشة: Home", "12:00:05 تصدير ZIP"),
        )
        assertNotNull(e)
        val text = assertNotNull(log.read(e.id))
        assertTrue(text.contains("kind: CRASH") && text.contains("title: java.lang.IllegalStateException: boom"))
        assertTrue(text.contains("version: 0.7.0") && text.contains("device: Pixel 7, Android 14"))
        assertTrue(text.contains("--- stack ---") && text.contains("IllegalStateException") && text.contains("Caused by"))
        assertTrue(text.contains("--- breadcrumbs ---") && text.contains("- 12:00:05 تصدير ZIP"))
        assertTrue(text.contains("UTC"))
    }

    @Test
    fun listIsNewestFirstWithKindAndTitle() = withDir { dir ->
        val log = newLog(dir)
        log.record(LogKind.WARNING, "أول")
        log.record(LogKind.ERROR, "ثان")
        log.record(LogKind.CRASH, "ثالث")
        val l = log.list()
        assertEquals(listOf("ثالث", "ثان", "أول"), l.map { it.title })
        assertEquals(listOf(LogKind.CRASH, LogKind.ERROR, LogKind.WARNING), l.map { it.kind })
        assertTrue(l[0].time > l[2].time)
    }

    @Test
    fun arabicTextSurvivesAndTitleIsCapped() = withDir { dir ->
        val log = newLog(dir)
        val e = assertNotNull(log.record(LogKind.ERROR, "فشل التصدير: " + "س".repeat(500) + "\nسطر ثان"))
        assertTrue(e.title.length <= ErrorLog.MAX_TITLE)
        assertFalse(e.title.contains('\n'))
        assertTrue(log.list().single().title.startsWith("فشل التصدير"))
    }

    @Test
    fun personalDataIsRedacted() = withDir { dir ->
        val log = newLog(dir)
        val msg = "تعذر فتح /data/user/0/com.arabicpagebuilder/files/projects/0f8fad5b-d9cb-469f-a165-70867728950e/project.json لـ me@example.com"
        val e = assertNotNull(log.record(LogKind.ERROR, msg, RuntimeException("/storage/emulated/0/Download/تقرير.pdf missing"), header = mapOf("note" to "mail a.b+c@d.org")))
        val text = assertNotNull(log.read(e.id))
        assertFalse(text.contains("/data/user") || text.contains("/storage/emulated") || text.contains("me@example.com") || text.contains("d.org") || text.contains("0f8fad5b"))
        assertTrue(text.contains("<مسار>") && text.contains("<بريد>") && text.contains("<معرّف>"))
    }

    @Test
    fun redactorLeavesNormalStackLinesAndArabicAlone() {
        val line = "at com.arabicpagebuilder.editor.EditorState.saveNow(EditorState.kt:94) فشل الحفظ"
        assertEquals(line, LogRedactor.redact(line))
        assertEquals("<مسار>", LogRedactor.redact("/data/user/0/app/files/x"))
        assertEquals("a <مسار> b", LogRedactor.redact("a /sdcard/DCIM/x.png b"))
        assertEquals("https://example.com/data/x", LogRedactor.redact("https://example.com/data/x")) // ليس مسارًا على الجهاز
    }

    @Test
    fun oldestEntriesAreRotatedOut() = withDir { dir ->
        val log = newLog(dir, max = 3)
        repeat(7) { log.record(LogKind.ERROR, "حدث $it") }
        assertEquals(listOf("حدث 6", "حدث 5", "حدث 4"), log.list().map { it.title })
    }

    @Test
    fun oversizedEntriesAreTruncated() = withDir { dir ->
        val log = newLog(dir, chars = 500)
        val e = assertNotNull(log.record(LogKind.CRASH, "t", RuntimeException("x".repeat(5000))))
        val text = assertNotNull(log.read(e.id))
        assertTrue(text.length < 700)
        assertTrue(text.contains("قُطع"))
    }

    @Test
    fun readRejectsUnsafeOrUnknownIds() = withDir { dir ->
        val log = newLog(dir)
        log.record(LogKind.ERROR, "x")
        File(dir, "secret.txt").writeText("سر")
        for (bad in listOf("../secret", "..", "", "1700000000001-000-ERROR/../../secret", "abc", "1700000000001-000-OTHER")) assertNull(log.read(bad), bad)
        assertNull(log.read("1700000000999-000-ERROR"))
    }

    @Test
    fun clearDeletesEverythingAndCountsIt() = withDir { dir ->
        val log = newLog(dir)
        repeat(4) { log.record(LogKind.WARNING, "w$it") }
        assertEquals(4, log.clear())
        assertTrue(log.list().isEmpty())
        assertEquals(0, log.clear())
    }

    @Test
    fun unseenCrashesAreCountedUntilMarkedSeen() = withDir { dir ->
        val log = newLog(dir)
        assertEquals(0, log.unseenCrashes())
        log.record(LogKind.ERROR, "ليس عطلًا")
        log.record(LogKind.CRASH, "عطل 1")
        log.record(LogKind.CRASH, "عطل 2")
        assertEquals(2, log.unseenCrashes())
        log.markSeen()
        assertEquals(0, log.unseenCrashes())
        log.record(LogKind.CRASH, "عطل 3")
        assertEquals(1, log.unseenCrashes())
    }

    @Test
    fun reportContainsHeaderPrivacyNoteAndEntriesNewestFirst() = withDir { dir ->
        val log = newLog(dir)
        log.record(LogKind.ERROR, "قديم")
        log.record(LogKind.CRASH, "جديد", RuntimeException("x"))
        val r = log.report(mapOf("الإصدار" to "0.7.0"))
        assertTrue(r.contains("الإصدار: 0.7.0"))
        assertTrue(r.contains("لا يحتوي هذا التقرير على محتوى مشاريعك"))
        assertTrue(r.indexOf("title: جديد") in 0 until r.indexOf("title: قديم"))
    }

    @Test
    fun reportDropsOldestWhenTooLong() = withDir { dir ->
        val log = newLog(dir)
        repeat(10) { log.record(LogKind.ERROR, "حدث $it", header = mapOf("تفاصيل" to "y".repeat(300))) }
        val r = log.report(maxReportChars = 2500)
        assertTrue(r.length <= 3000)
        assertTrue(r.contains("title: حدث 9"))
        assertTrue(r.contains("حُذفت"))
    }

    @Test
    fun loggingNeverThrowsEvenWhenTheFolderIsUnusable() = withDir { dir ->
        val blocker = File(dir, "logs").also { it.writeText("ملف وليس مجلدًا") }
        val log = ErrorLog(blocker)
        assertNull(log.record(LogKind.CRASH, "x", RuntimeException("y")))
        assertTrue(log.list().isEmpty())
        assertNull(log.read("1700000000001-000-ERROR"))
        log.markSeen()
        assertEquals(0, log.unseenCrashes())
        assertEquals(0, log.clear())
    }

    @Test
    fun concurrentRecordingProducesUniqueEntries() = withDir { dir ->
        val log = ErrorLog(File(dir, "logs"), clock = { 1_700_000_000_000L }, maxEntries = 200)
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        repeat(40) { i -> pool.execute { start.await(); log.record(LogKind.ERROR, "متزامن $i") } }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS))
        val l = log.list()
        assertEquals(40, l.size)
        assertEquals(40, l.map { it.id }.toSet().size)
    }

    @Test
    fun breadcrumbsKeepOnlyTheLastOnesAndRedact() {
        var t = 1_700_000_000_000L
        val b = Breadcrumbs(max = 3, clock = { t += 1000; t })
        for (i in 1..5) b.add("خطوة $i")
        b.add("فتح /data/user/0/app/files/x")
        val s = b.snapshot()
        assertEquals(3, s.size)
        assertTrue(s[0].endsWith("خطوة 4") && s[1].endsWith("خطوة 5") && s[2].endsWith("<مسار>"))
        assertTrue(Regex("^\\d{2}:\\d{2}:\\d{2} ").containsMatchIn(s[0]))
    }
}

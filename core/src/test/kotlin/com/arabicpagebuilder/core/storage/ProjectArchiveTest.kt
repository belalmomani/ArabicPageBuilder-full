package com.arabicpagebuilder.core.storage

import com.arabicpagebuilder.core.edit.DocumentEditor
import com.arabicpagebuilder.core.edit.EditResult
import com.arabicpagebuilder.core.export.ExportLimits
import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.PageDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

class ProjectArchiveTest {

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)

    /** sandbox/ فيه مجلدان: a (مخزن أول) وb (مخزن ثان)؛ وملف خارجهما للتأكد من عدم الكتابة خارج المخزن. */
    private class Sandbox(val dir: File) {
        val rootA = File(dir, "a").also { it.mkdirs() }
        val rootB = File(dir, "b").also { it.mkdirs() }
        val storeA = ProjectStore(rootA)
        val storeB = ProjectStore(rootB)
    }

    private fun <T> withSandbox(block: (Sandbox) -> T): T {
        val dir = Files.createTempDirectory("apb-arch").toFile()
        try {
            return block(Sandbox(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun <T> StoreResult<T>.ok(): T = when (this) {
        is StoreResult.Ok -> value
        is StoreResult.Err -> throw AssertionError("متوقع Ok لكن النتيجة: $this")
    }

    private fun StoreResult<*>.err(): StoreError = assertIs<StoreResult.Err>(this).error

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, bytes) in entries) {
                z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun validProjectJson(doc: PageDocument = PageDocument.empty("ع")): ByteArray =
        ProjectFormat.encode(ProjectData("مستورد", 10L, 20L, doc)).toByteArray(Charsets.UTF_8)

    private fun importBytes(store: ProjectStore, bytes: ByteArray, limits: ImportLimits = ImportLimits()) =
        ProjectArchive(store).importProject(ByteArrayInputStream(bytes), limits)

    /** مشروع فيه عنوان وصورة عربية الاسم، محفوظ. */
    private fun seedProject(store: ProjectStore): Pair<LoadedProject, String> {
        val p = store.create("مشروعي", "عنوان").ok()
        val path = store.addAsset(p.id, "صورة عائلة.png", ByteArrayInputStream(png)).ok()
        var doc = p.data.document
        doc = (DocumentEditor.add(doc, doc.root.id, Element("h", ElementType.HEADING, mapOf("text" to "مرحبًا \"بكم\" <&>"))) as EditResult.Success).document
        doc = (DocumentEditor.add(doc, doc.root.id, Element("img", ElementType.IMAGE, mapOf("src" to path, "alt" to "وصف"))) as EditResult.Success).document
        return store.save(p.id, doc).ok() to path
    }

    private fun exportBytes(store: ProjectStore, id: String): ByteArray {
        val out = ByteArrayOutputStream()
        ProjectArchive(store).exportProject(id, out).ok()
        return out.toByteArray()
    }

    private fun assertRejectedCleanly(s: Sandbox, bytes: ByteArray, expected: StoreError = StoreError.INVALID_ARCHIVE, limits: ImportLimits = ImportLimits()) {
        assertEquals(expected, importBytes(s.storeB, bytes, limits).err())
        assertTrue(s.storeB.list().isEmpty())
        assertEquals(emptyList(), s.rootB.list()!!.toList(), "لا يجوز بقاء أي أثر في المخزن")
        assertEquals(setOf("a", "b"), s.dir.list()!!.toSet(), "لا يجوز الكتابة خارج المخزن")
    }

    // ---- تصدير واستيراد ----

    @Test
    fun exportThenImportIntoAnotherStoreRoundTrips() = withSandbox { s ->
        val (original, path) = seedProject(s.storeA)
        val bytes = exportBytes(s.storeA, original.id)

        val imported = importBytes(s.storeB, bytes).ok()
        assertNotEquals(original.id, imported.project.id)
        assertTrue(imported.missingAssets.isEmpty())
        assertEquals(original.data.document, imported.project.data.document)
        assertEquals("مشروعي", imported.project.data.name)

        val reopened = ProjectStore(s.rootB).load(imported.project.id).ok()
        assertEquals(original.data.document, reopened.data.document)
        val asset = s.storeB.assetSource(imported.project.id)!!
        assertContentEquals(png, asset.open(path)!!.use { it.readBytes() })
        assertEquals(1, s.storeA.list().size) // الأصل لم يتغير
    }

    @Test
    fun importIntoSameStoreCreatesSecondProjectAndNeverOverwrites() = withSandbox { s ->
        val (original, _) = seedProject(s.storeA)
        val bytes = exportBytes(s.storeA, original.id)
        val imported = importBytes(s.storeA, bytes).ok()
        assertNotEquals(original.id, imported.project.id)
        assertEquals(2, s.storeA.list().size)
    }

    @Test
    fun exportToFileProducesVerifiableArchiveAndNoTempFiles() = withSandbox { s ->
        val (p, _) = seedProject(s.storeA)
        val target = File(s.dir, "backup.${ProjectArchive.EXTENSION}")
        ProjectArchive(s.storeA).exportProjectToFile(p.id, target).ok()
        assertEquals(emptyList(), ProjectArchive(s.storeA).verify(target))
        assertEquals(setOf("a", "b", "backup.apbproj"), s.dir.list()!!.toSet())
        assertTrue(importBytes(s.storeB, target.readBytes()).ok().project.data.document == p.data.document)
    }

    @Test
    fun failedExportKeepsExistingTargetUntouched() = withSandbox { s ->
        val target = File(s.dir, "backup.apbproj")
        target.writeText("OLD")
        assertEquals(StoreError.NOT_FOUND, ProjectArchive(s.storeA).exportProjectToFile("nope", target).err())
        assertEquals("OLD", target.readText())
        val (p, _) = seedProject(s.storeA)
        val r = ProjectArchive(s.storeA).exportProjectToFile(p.id, target, ExportLimits(maxAssetBytes = 2))
        assertEquals(StoreError.ASSET_TOO_LARGE, r.err())
        assertEquals("OLD", target.readText())
        assertEquals(setOf("a", "b", "backup.apbproj"), s.dir.list()!!.toSet())
    }

    @Test
    fun missingReferencedAssetsAreReportedNotFatal() = withSandbox { s ->
        val doc = PageDocument.empty("ع").let {
            it.copy(root = it.root.copy(children = listOf(Element("i", ElementType.IMAGE, mapOf("src" to "assets/images/gone.png", "alt" to "x")))))
        }
        val r = importBytes(s.storeB, zipOf("project.json" to validProjectJson(doc))).ok()
        assertEquals(listOf("assets/images/gone.png"), r.missingAssets)
    }

    @Test
    fun arabicAssetNamesSurviveRoundTrip() = withSandbox { s ->
        val (p, path) = seedProject(s.storeA)
        assertEquals("assets/images/صورة_عائلة.png", path)
        val imported = importBytes(s.storeB, exportBytes(s.storeA, p.id)).ok()
        assertTrue(s.storeB.assetSource(imported.project.id)!!.exists(path))
    }

    // ---- الرفض الآمن ----

    @Test
    fun zipSlipAndUnsafePathsAreRejected() = withSandbox { s ->
        val pj = "project.json" to validProjectJson()
        for (evil in listOf("../evil.txt", "assets/../../evil.png", "/abs.txt", "assets/images/../../../evil.png", "a\\b.png", "assets/images/C:evil.png")) {
            assertRejectedCleanly(s, zipOf(pj, evil to byteArrayOf(1)))
            assertFalse(File(s.dir, "evil.txt").exists())
            assertFalse(File(s.dir, "evil.png").exists())
        }
    }

    @Test
    fun unexpectedEntriesAreRejected() = withSandbox { s ->
        val pj = "project.json" to validProjectJson()
        for (bad in listOf("other.txt", "index.html", "assets/images/evil.exe", "assets/images/x.svg", "assets/images/sub/x.png", "assets/other/x.png", "assets/images/x.PNG", "assets/media/x.png", "assets/images/.png")) {
            assertRejectedCleanly(s, zipOf(pj, bad to byteArrayOf(1)))
        }
    }

    @Test
    fun caseCollidingEntriesAreRejected() = withSandbox { s ->
        assertRejectedCleanly(s, zipOf("project.json" to validProjectJson(), "assets/images/a.png" to png, "assets/images/A.png" to png))
    }

    @Test
    fun missingOrInvalidProjectJsonIsRejected() = withSandbox { s ->
        assertRejectedCleanly(s, zipOf("assets/images/a.png" to png))
        assertRejectedCleanly(s, zipOf("project.json" to "{ not json".toByteArray()))
        val future = String(validProjectJson(), Charsets.UTF_8).replace("\"schemaVersion\":1", "\"schemaVersion\":42")
        assertRejectedCleanly(s, zipOf("project.json" to future.toByteArray()))
        assertRejectedCleanly(s, zipOf())
    }

    @Test
    fun garbageInputIsRejectedWithoutLeavingTraces() = withSandbox { s ->
        assertRejectedCleanly(s, "this is definitely not a zip file".toByteArray())
        assertRejectedCleanly(s, ByteArray(0))
    }

    @Test
    fun entryCountLimitIsEnforced() = withSandbox { s ->
        val bytes = zipOf("project.json" to validProjectJson(), "assets/images/a.png" to png, "assets/images/b.png" to png)
        assertRejectedCleanly(s, bytes, StoreError.ARCHIVE_TOO_LARGE, ImportLimits(maxEntries = 2))
    }

    @Test
    fun oversizedEntryIsRejectedByActualDecompressedSize() = withSandbox { s ->
        // 5 ميغابايت أصفار تُضغط إلى بضع كيلوبايتات: الحد يُطبَّق على البايتات المفكوكة فعلًا.
        val bomb = ByteArray(5 * 1024 * 1024)
        val bytes = zipOf("project.json" to validProjectJson(), "assets/images/bomb.png" to bomb)
        assertTrue(bytes.size < 100_000)
        assertRejectedCleanly(s, bytes, StoreError.ARCHIVE_TOO_LARGE, ImportLimits(maxEntryBytes = 1024 * 1024))
    }

    @Test
    fun totalSizeLimitIsEnforced() = withSandbox { s ->
        val bytes = zipOf(
            "project.json" to validProjectJson(),
            "assets/images/a.png" to ByteArray(60_000),
            "assets/images/b.png" to ByteArray(60_000),
        )
        assertRejectedCleanly(s, bytes, StoreError.ARCHIVE_TOO_LARGE, ImportLimits(maxEntryBytes = 100_000, maxTotalBytes = 100_000))
    }

    // ---- التحقق ----

    @Test
    fun verifyFlagsBadArchives() = withSandbox { s ->
        val f = File(s.dir, "bad.zip")
        f.writeBytes(zipOf("project.json" to validProjectJson(), "other.txt" to byteArrayOf(1), "../evil" to byteArrayOf(1)))
        val problems = ProjectArchive(s.storeA).verify(f)
        assertTrue(problems.size >= 2, problems.toString())
        f.writeText("not a zip")
        assertTrue(ProjectArchive(s.storeA).verify(f).isNotEmpty())
    }

    @Test
    fun filesAndMediaSurviveRoundTripAndExeIsRejected() = withSandbox { s ->
        // الرفض أولًا (يتحقق أن المخزن الهدف بقي فارغًا تمامًا)
        assertRejectedCleanly(s, zipOf("project.json" to validProjectJson(), "assets/files/evil.exe" to byteArrayOf(1)))
        val p = s.storeA.create("م", "ع").ok()
        val pdf = s.storeA.addAsset(p.id, "cv.pdf", ByteArrayInputStream(png)).ok()
        val mp4 = s.storeA.addAsset(p.id, "clip.mp4", ByteArrayInputStream(png)).ok()
        val imported = importBytes(s.storeB, exportBytes(s.storeA, p.id)).ok()
        val src = s.storeB.assetSource(imported.project.id)!!
        assertTrue(src.exists(pdf) && src.exists(mp4))
    }

    @Test
    fun importRejectsImagesWhoseContentDoesNotMatchExtension() = withSandbox { s ->
        val fake = "<html>not an image</html>".toByteArray()
        assertRejectedCleanly(s, zipOf("project.json" to validProjectJson(), "assets/images/fake.png" to fake))
        assertRejectedCleanly(s, zipOf("project.json" to validProjectJson(), "assets/images/real.jpg" to png)) // PNG بامتداد jpg
        // الصورة السليمة تُقبل
        assertTrue(importBytes(s.storeB, zipOf("project.json" to validProjectJson(), "assets/images/ok.png" to png)) is StoreResult.Ok)
    }
}

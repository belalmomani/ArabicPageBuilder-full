package com.arabicpagebuilder.core.storage

import com.arabicpagebuilder.core.edit.DocumentEditor
import com.arabicpagebuilder.core.edit.EditResult
import com.arabicpagebuilder.core.export.SiteExporter
import com.arabicpagebuilder.core.export.ExportResult
import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.PageDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectStoreTest {

    private var tick = 1_000L
    private fun newStore(root: File) = ProjectStore(root, clock = { ++tick })

    private fun <T> withStore(block: (ProjectStore, File) -> T): T {
        val dir = Files.createTempDirectory("apb-store").toFile()
        try {
            return block(newStore(dir), dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun <T> StoreResult<T>.ok(): T = when (this) {
        is StoreResult.Ok -> value
        is StoreResult.Err -> throw AssertionError("متوقع Ok لكن النتيجة: $this")
    }
    private fun StoreResult<*>.err(): StoreError = assertIs<StoreResult.Err>(this).error

    private fun withHeading(doc: PageDocument, id: String, text: String): PageDocument =
        (DocumentEditor.add(doc, doc.root.id, Element(id, ElementType.HEADING, mapOf("text" to text))) as EditResult.Success).document

    private val png = byteArrayOf(9, 8, 7, 6, 5, 4)

    @Test
    fun createThenLoadAndReopenWithNewStoreInstance() = withStore { store, root ->
        val created = store.create("مشروعي", "عنوان الصفحة").ok()
        assertEquals("مشروعي", created.data.name)
        assertTrue(created.data.document.root.children.isEmpty())
        val reopened = newStore(root).load(created.id).ok()
        assertEquals(created.data, reopened.data)
    }

    @Test
    fun saveCloseAndReopenKeepsDocument() = withStore { store, root ->
        val p = store.create("م", "ع").ok()
        val doc = withHeading(p.data.document, "h1", "نص عربي \"مع\" رموز <>&")
        store.save(p.id, doc).ok()
        assertEquals(doc, newStore(root).load(p.id).ok().data.document)
    }

    @Test
    fun unchangedSaveIsNoOpAndKeepsOlderBackup() = withStore { store, _ ->
        val p = store.create("م", "ع").ok()
        val a = withHeading(p.data.document, "h1", "A")
        store.save(p.id, a).ok()
        store.save(p.id, a).ok() // لا تغيير: لا يجوز أن يستبدل النسخة الاحتياطية
        val restored = store.restoreBackup(p.id).ok()
        assertEquals(p.data.document, restored.data.document)
    }

    @Test
    fun restoreBackupReturnsPreviousSavedVersion() = withStore { store, root ->
        val p = store.create("م", "ع").ok()
        val a = withHeading(p.data.document, "h1", "A")
        val b = withHeading(a, "h2", "B")
        store.save(p.id, a).ok()
        store.save(p.id, b).ok()
        assertEquals(a, store.restoreBackup(p.id).ok().data.document)
        assertEquals(a, newStore(root).load(p.id).ok().data.document)
    }

    @Test
    fun restoreWithoutBackupFails() = withStore { store, _ ->
        val p = store.create("م", "ع").ok()
        assertEquals(StoreError.NO_BACKUP, store.restoreBackup(p.id).err())
    }

    @Test
    fun corruptFileIsReportedListedAndRecoverableBySaving() = withStore { store, root ->
        val p = store.create("م", "ع").ok()
        val a = withHeading(p.data.document, "h1", "A")
        store.save(p.id, a).ok()
        File(File(root, p.id), ProjectStore.PROJECT_FILE).writeText("{ تالف")
        assertEquals(StoreError.CORRUPT, store.load(p.id).err())
        val summary = store.list().single()
        assertTrue(summary.corrupt)
        assertTrue(summary.hasBackup)
        // الحفظ فوق ملف تالف يعمل ولا يفسد النسخة الاحتياطية السليمة
        val b = withHeading(a, "h2", "B")
        store.save(p.id, b).ok()
        assertEquals(b, store.load(p.id).ok().data.document)
        assertEquals(p.data.document, store.restoreBackup(p.id).ok().data.document)
    }

    @Test
    fun noTempFilesRemainAfterSaves() = withStore { store, root ->
        val p = store.create("م", "ع").ok()
        store.save(p.id, withHeading(p.data.document, "h1", "A")).ok()
        store.save(p.id, withHeading(p.data.document, "h2", "B")).ok()
        assertEquals(setOf("project.json", "project.json.bak"), File(root, p.id).list()!!.toSet())
    }

    @Test
    fun listIsSortedByMostRecentlyModified() = withStore { store, _ ->
        val first = store.create("أول", "ع").ok()
        val second = store.create("ثاني", "ع").ok()
        assertEquals(listOf(second.id, first.id), store.list().map { it.id })
        store.save(first.id, withHeading(first.data.document, "h", "x")).ok()
        assertEquals(listOf(first.id, second.id), store.list().map { it.id })
    }

    @Test
    fun renameValidatesAndUpdatesName() = withStore { store, _ ->
        val p = store.create("قديم", "ع").ok()
        assertEquals("جديد", store.rename(p.id, "  جديد ").ok().data.name)
        assertEquals("جديد", store.load(p.id).ok().data.name)
        assertEquals(StoreError.INVALID_NAME, store.rename(p.id, "   ").err())
        assertEquals(StoreError.INVALID_NAME, store.rename(p.id, "x".repeat(201)).err())
        assertEquals(StoreError.INVALID_NAME, store.rename(p.id, "a\u0000b").err())
        assertEquals(StoreError.INVALID_NAME, store.create("", "ع").err())
    }

    @Test
    fun duplicateCopiesDocumentAndAssetsUnderNewId() = withStore { store, root ->
        val p = store.create("أصلي", "ع").ok()
        store.save(p.id, withHeading(p.data.document, "h", "x")).ok()
        val path = store.addAsset(p.id, "a.png", ByteArrayInputStream(png)).ok()
        val copy = store.duplicate(p.id).ok()
        assertNotEquals(p.id, copy.id)
        assertEquals("أصلي (نسخة)", copy.data.name)
        assertEquals(store.load(p.id).ok().data.document, copy.data.document)
        assertTrue(File(File(root, copy.id), path).isFile)
        assertEquals("أصلي", store.load(p.id).ok().data.name)
    }

    @Test
    fun deleteRemovesProjectAndSecondDeleteFails() = withStore { store, root ->
        val p = store.create("م", "ع").ok()
        store.delete(p.id).ok()
        assertFalse(File(root, p.id).exists())
        assertEquals(StoreError.NOT_FOUND, store.delete(p.id).err())
        assertEquals(StoreError.NOT_FOUND, store.load(p.id).err())
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun unsafeProjectIdsAreRejectedEverywhere() = withStore { store, _ ->
        for (bad in listOf("../x", "a/b", "", "..", "a b", "x".repeat(65))) {
            assertEquals(StoreError.INVALID_ID, store.load(bad).err(), bad)
            assertEquals(StoreError.INVALID_ID, store.delete(bad).err(), bad)
            assertEquals(StoreError.INVALID_ID, store.restoreBackup(bad).err(), bad)
            assertEquals(StoreError.INVALID_ID, store.rename(bad, "x").err(), bad)
            assertNull(store.assetSource(bad), bad)
        }
    }

    // ---- الأصول ----

    @Test
    fun assetNamesAreSanitizedAndKeptInsideAssetsFolder() = withStore { store, root ->
        val p = store.create("م", "ع").ok()
        assertEquals("assets/images/evil.png", store.addAsset(p.id, "../../evil.png", ByteArrayInputStream(png)).ok())
        assertEquals("assets/images/صورة_عائلة.png", store.addAsset(p.id, "صورة عائلة.PNG", ByteArrayInputStream(png)).ok())
        assertEquals("assets/images/my_photo_1.jpg", store.addAsset(p.id, "my photo (1).jpg", ByteArrayInputStream(png)).ok())
        assertEquals("assets/media/clip.mp4", store.addAsset(p.id, "clip.mp4", ByteArrayInputStream(png)).ok())
        assertTrue(File(File(root, p.id), "assets/images/evil.png").isFile)
        assertFalse(File(root, "evil.png").exists())
    }

    @Test
    fun duplicateAssetNamesGetNumericSuffix() = withStore { store, _ ->
        val p = store.create("م", "ع").ok()
        assertEquals("assets/images/a.png", store.addAsset(p.id, "a.png", ByteArrayInputStream(png)).ok())
        assertEquals("assets/images/a-2.png", store.addAsset(p.id, "a.png", ByteArrayInputStream(png)).ok())
        assertEquals("assets/images/a-3.png", store.addAsset(p.id, "a.png", ByteArrayInputStream(png)).ok())
    }

    @Test
    fun disallowedAssetTypesAreRejected() = withStore { store, _ ->
        val p = store.create("م", "ع").ok()
        for (bad in listOf("x.svg", "x.exe", "x.html", "x.png.exe", "noext", ".png", "x.", "x.js")) {
            assertEquals(StoreError.ASSET_NOT_ALLOWED, store.addAsset(p.id, bad, ByteArrayInputStream(png)).err(), bad)
        }
    }

    @Test
    fun oversizedAssetIsRejectedAndLeavesNoPartialFile() = withStore { store, root ->
        val p = store.create("م", "ع").ok()
        val r = store.addAsset(p.id, "big.png", ByteArrayInputStream(ByteArray(1000)), maxBytes = 100)
        assertEquals(StoreError.ASSET_TOO_LARGE, r.err())
        val images = File(File(root, p.id), "assets/images")
        assertTrue(images.list().isNullOrEmpty())
    }

    @Test
    fun assetSourceServesOnlyFilesInsideAssets() = withStore { store, _ ->
        val p = store.create("م", "ع").ok()
        val path = store.addAsset(p.id, "a.png", ByteArrayInputStream(png)).ok()
        val src = assertNotNull(store.assetSource(p.id))
        assertTrue(src.exists(path))
        assertContentEquals(png, src.open(path)!!.use { it.readBytes() })
        for (bad in listOf("project.json", "assets/../project.json", "../x", "/etc/passwd", "assets/images/missing.png", "assets/images", "assets")) {
            assertFalse(src.exists(bad), bad)
            assertNull(src.open(bad), bad)
        }
    }

    @Test
    fun savedProjectExportsToZipWithItsAssets() = withStore { store, _ ->
        val p = store.create("م", "عنوان").ok()
        val path = store.addAsset(p.id, "photo.png", ByteArrayInputStream(png)).ok()
        val withImage = (DocumentEditor.add(
            p.data.document, p.data.document.root.id,
            Element("img1", ElementType.IMAGE, mapOf("src" to path, "alt" to "صورة")),
        ) as EditResult.Success).document
        store.save(p.id, withImage).ok()
        val loaded = store.load(p.id).ok()
        val out = ByteArrayOutputStream()
        val result = SiteExporter.export(loaded.data.document, store.assetSource(p.id)!!, out)
        val success = assertIs<ExportResult.Success>(result)
        assertTrue(success.warnings.isEmpty())
        val names = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z -> while (true) { names.add((z.nextEntry ?: break).name) } }
        assertTrue("website-project/assets/images/photo.png" in names)
    }

    @Test
    fun documentsAndOtherAllowedFilesGoToFilesFolder() = withStore { store, _ ->
        val p = store.create("م", "ع").ok()
        assertEquals("assets/files/cv.pdf", store.addAsset(p.id, "cv.pdf", ByteArrayInputStream(png)).ok())
        assertEquals("assets/files/تقرير_2026.docx", store.addAsset(p.id, "تقرير 2026.docx", ByteArrayInputStream(png)).ok())
        assertEquals("assets/files/data.csv", store.addAsset(p.id, "../data.csv", ByteArrayInputStream(png)).ok())
        assertTrue(store.assetSource(p.id)!!.exists("assets/files/cv.pdf"))
        assertEquals(StoreError.ASSET_NOT_ALLOWED, store.addAsset(p.id, "run.exe", ByteArrayInputStream(png)).err())
        assertEquals(StoreError.ASSET_NOT_ALLOWED, store.addAsset(p.id, "page.html", ByteArrayInputStream(png)).err())
    }

    @Test
    fun cleanupRemovesOnlyStaleImportFolders() {
        val dir = Files.createTempDirectory("apb-clean").toFile()
        try {
            val store = ProjectStore(dir) // الساعة الحقيقية
            val project = store.create("م", "ع").ok()
            val stale = File(dir, ".import-old").also { it.mkdirs(); File(it, "x.txt").writeText("x"); it.setLastModified(1_000L) }
            val fresh = File(dir, ".import-new").also { it.mkdirs() }
            assertEquals(1, store.cleanupStaleTemp(olderThanMillis = 60_000L))
            assertFalse(stale.exists())
            assertTrue(fresh.exists())
            assertTrue(File(dir, project.id).isDirectory) // المشاريع لا تُمس
        } finally {
            dir.deleteRecursively()
        }
    }
}

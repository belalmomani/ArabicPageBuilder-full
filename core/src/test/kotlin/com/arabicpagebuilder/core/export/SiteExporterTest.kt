package com.arabicpagebuilder.core.export

import com.arabicpagebuilder.core.generator.HtmlGenerator
import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.IdGenerator
import com.arabicpagebuilder.core.model.PageDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SiteExporterTest {

    private class MapAssets(private val files: Map<String, ByteArray>) : AssetSource {
        override fun exists(path: String) = path in files
        override fun open(path: String): InputStream? = files[path]?.let { ByteArrayInputStream(it) }
    }

    private fun doc(vararg children: Element, title: String = "موقعي"): PageDocument {
        val empty = PageDocument.empty(title, ids = IdGenerator { "root" })
        return empty.copy(root = empty.root.copy(children = children.toList()))
    }

    private fun img(id: String, src: String, alt: String = "وصف") =
        Element(id, ElementType.IMAGE, mapOf("src" to src, "alt" to alt))

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                result[e.name] = zin.readBytes()
            }
        }
        return result
    }

    private fun exportBytes(d: PageDocument, assets: AssetSource, limits: ExportLimits = ExportLimits()): Pair<ExportResult, ByteArray> {
        val out = ByteArrayOutputStream()
        val r = SiteExporter.export(d, assets, out, limits)
        return r to out.toByteArray()
    }

    private fun <T> withTempDir(block: (File) -> T): T {
        val dir = Files.createTempDirectory("apb-test").toFile()
        try {
            return block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private val png = byteArrayOf(1, 2, 3, 4, 5, 0, 127, -1)

    @Test
    fun containsExpectedEntriesUnderRootFolderAndOnlyUsedAssets() {
        val d = doc(img("i1", "assets/images/a.png"))
        val assets = MapAssets(mapOf("assets/images/a.png" to png, "assets/images/unused.png" to png))
        val (r, bytes) = exportBytes(d, assets)
        val success = assertIs<ExportResult.Success>(r)
        val zip = readZip(bytes)
        assertEquals(
            setOf(
                "website-project/index.html",
                "website-project/css/style.css",
                "website-project/assets/images/a.png",
                "website-project/README.txt",
            ),
            zip.keys,
        )
        assertEquals(zip.keys.toList(), success.entries)
        assertTrue(success.warnings.isEmpty())
    }

    @Test
    fun assetBytesAndGeneratedFilesArePreservedExactly() {
        val d = doc(img("i1", "assets/images/a.png"))
        val zip = readZip(exportBytes(d, MapAssets(mapOf("assets/images/a.png" to png))).second)
        assertContentEquals(png, zip.getValue("website-project/assets/images/a.png"))
        val site = HtmlGenerator.generate(d)
        assertEquals(site.indexHtml, String(zip.getValue("website-project/index.html"), Charsets.UTF_8))
        assertEquals(site.styleCss, String(zip.getValue("website-project/css/style.css"), Charsets.UTF_8))
    }

    @Test
    fun arabicFileNamesAndTextSurviveRoundTrip() {
        val d = doc(
            img("i1", "assets/images/صورة-عائلة.png", alt = "عائلة"),
            Element("p", ElementType.PARAGRAPH, mapOf("text" to "مرحبًا بالعالم Hello")),
            title = "عنوان عربي",
        )
        val zip = readZip(exportBytes(d, MapAssets(mapOf("assets/images/صورة-عائلة.png" to png))).second)
        assertTrue("website-project/assets/images/صورة-عائلة.png" in zip.keys)
        val html = String(zip.getValue("website-project/index.html"), Charsets.UTF_8)
        assertTrue(html.contains("مرحبًا بالعالم Hello"))
        assertTrue(html.contains("<title>عنوان عربي</title>"))
        assertTrue(String(zip.getValue("website-project/README.txt"), Charsets.UTF_8).contains("عنوان عربي"))
    }

    @Test
    fun sameImageUsedTwiceIsCopiedOnce() {
        val d = doc(img("i1", "assets/images/a.png"), img("i2", "assets/images/a.png"))
        val (r, bytes) = exportBytes(d, MapAssets(mapOf("assets/images/a.png" to png)))
        assertIs<ExportResult.Success>(r)
        assertEquals(1, readZip(bytes).keys.count { it.endsWith("a.png") })
    }

    @Test
    fun missingImageGivesWarningAndReadmeMentionsIt() {
        val d = doc(img("i1", "assets/images/gone.png"))
        val (r, bytes) = exportBytes(d, MapAssets(emptyMap()))
        val success = assertIs<ExportResult.Success>(r)
        assertEquals(listOf(ExportWarning(ExportWarningCode.MISSING_ASSET, "assets/images/gone.png", "i1")), success.warnings)
        val readme = String(readZip(bytes).getValue("website-project/README.txt"), Charsets.UTF_8)
        assertTrue(readme.contains("assets/images/gone.png"))
    }

    @Test
    fun localLinkTargetsAreCheckedAndExternalOnesAreNot() {
        val d = doc(
            Element("l1", ElementType.LINK, mapOf("text" to "x", "href" to "assets/files/cv.pdf")),
            Element("l2", ElementType.LINK, mapOf("text" to "y", "href" to "other.html")),
            Element("l3", ElementType.LINK, mapOf("text" to "z", "href" to "https://example.com")),
            Element("l4", ElementType.LINK, mapOf("text" to "w", "href" to "#top")),
        )
        val (r, bytes) = exportBytes(d, MapAssets(mapOf("assets/files/cv.pdf" to png)))
        val success = assertIs<ExportResult.Success>(r)
        assertEquals(listOf(ExportWarning(ExportWarningCode.LOCAL_LINK_TARGET_MISSING, "other.html", "l2")), success.warnings)
        assertTrue("website-project/assets/files/cv.pdf" in readZip(bytes).keys)
    }

    @Test
    fun devicePrivatePathsNeverReachTheArchive() {
        val d = doc(img("i1", "/data/user/0/com.app/files/x.png"), img("i2", "file:///sdcard/y.png"))
        val (r, bytes) = exportBytes(d, MapAssets(emptyMap()))
        assertIs<ExportResult.Success>(r)
        val zip = readZip(bytes)
        for ((name, content) in zip) {
            assertTrue(ZipPaths.isSafeEntryName(name), name)
            val text = String(content, Charsets.UTF_8)
            assertFalse(text.contains("/data/user"), name)
            assertFalse(text.contains("/sdcard"), name)
        }
        assertFalse(zip.keys.any { it.contains("assets") })
    }

    @Test
    fun readmeExplainsRunningAndServerLimits() {
        val readme = String(readZip(exportBytes(doc(), MapAssets(emptyMap())).second).getValue("website-project/README.txt"), Charsets.UTF_8)
        assertTrue(readme.contains("index.html"))
        assertTrue(readme.contains("خادمًا"))
    }

    @Test
    fun assetOverPerFileLimitFails() {
        val d = doc(img("i1", "assets/images/a.png"))
        val (r, _) = exportBytes(d, MapAssets(mapOf("assets/images/a.png" to ByteArray(100))), ExportLimits(maxAssetBytes = 50))
        assertEquals(ExportResult.Failure(ExportError.ASSET_TOO_LARGE, "assets/images/a.png"), r)
    }

    @Test
    fun totalOverLimitFails() {
        val d = doc(img("i1", "assets/images/a.png"), img("i2", "assets/images/b.png"))
        val assets = MapAssets(mapOf("assets/images/a.png" to ByteArray(60_000), "assets/images/b.png" to ByteArray(60_000)))
        val (r, _) = exportBytes(d, assets, ExportLimits(maxAssetBytes = 100_000, maxTotalBytes = 100_000))
        assertEquals(ExportError.TOTAL_TOO_LARGE, assertIs<ExportResult.Failure>(r).error)
    }

    @Test
    fun failingOutputStreamReturnsIoError() {
        val failing = object : OutputStream() {
            override fun write(b: Int) = throw IOException("no space left")
            override fun write(b: ByteArray, off: Int, len: Int) = throw IOException("no space left")
        }
        val r = SiteExporter.export(doc(), MapAssets(emptyMap()), failing)
        assertEquals(ExportError.IO_ERROR, assertIs<ExportResult.Failure>(r).error)
    }

    @Test
    fun exportToFileSucceedsAndResultVerifiesClean() = withTempDir { dir ->
        val target = File(dir, "site.zip")
        val d = doc(img("i1", "assets/images/a.png"))
        val r = SiteExporter.exportToFile(d, MapAssets(mapOf("assets/images/a.png" to png)), target)
        assertIs<ExportResult.Success>(r)
        assertTrue(target.isFile)
        assertEquals(emptyList(), ExportVerifier.verify(target))
        assertEquals(listOf("site.zip"), dir.list()!!.toList()) // لا ملفات مؤقتة متبقية
    }

    @Test
    fun failedExportKeepsExistingTargetAndLeavesNoTempFiles() = withTempDir { dir ->
        val target = File(dir, "site.zip")
        target.writeText("OLD")
        val d = doc(img("i1", "assets/images/a.png"))
        val r = SiteExporter.exportToFile(d, MapAssets(mapOf("assets/images/a.png" to ByteArray(100))), target, ExportLimits(maxAssetBytes = 10))
        assertIs<ExportResult.Failure>(r)
        assertEquals("OLD", target.readText())
        assertEquals(listOf("site.zip"), dir.list()!!.toList())
    }

    @Test
    fun exportToMissingDirectoryFailsCleanly() = withTempDir { dir ->
        val r = SiteExporter.exportToFile(doc(), MapAssets(emptyMap()), File(File(dir, "nope"), "site.zip"))
        assertEquals(ExportError.IO_ERROR, assertIs<ExportResult.Failure>(r).error)
    }

    @Test
    fun outputIsByteForByteDeterministic() {
        val d = doc(img("i1", "assets/images/a.png"))
        val assets = MapAssets(mapOf("assets/images/a.png" to png))
        assertContentEquals(exportBytes(d, assets).second, exportBytes(d, assets).second)
    }

    // ---- المدقّق ----

    private fun craftZip(dir: File, build: (ZipOutputStream) -> Unit): File {
        val f = File(dir, "crafted.zip")
        ZipOutputStream(f.outputStream()).use(build)
        return f
    }

    private fun ZipOutputStream.add(name: String, text: String) {
        putNextEntry(ZipEntry(name)); write(text.toByteArray()); closeEntry()
    }

    @Test
    fun verifierFlagsZipSlipEntriesAndMissingIndex() = withTempDir { dir ->
        val f = craftZip(dir) { it.add("website-project/../evil.txt", "x"); it.add("/abs.txt", "y") }
        val problems = ExportVerifier.verify(f)
        assertTrue(problems.any { it.contains("غير آمن") })
        assertTrue(problems.any { it.contains("index.html") })
    }

    @Test
    fun verifierFlagsReferencesToMissingFiles() = withTempDir { dir ->
        val f = craftZip(dir) {
            it.add("website-project/index.html", "<img src=\"assets/x.png\"><a href=\"https://a.com\">a</a><a href=\"#t\">t</a>")
        }
        val problems = ExportVerifier.verify(f)
        assertEquals(1, problems.size)
        assertTrue(problems.single().contains("assets/x.png"))
    }

    @Test
    fun verifierReportsCorruptFile() = withTempDir { dir ->
        val f = File(dir, "bad.zip")
        f.writeText("this is not a zip")
        assertTrue(ExportVerifier.verify(f).isNotEmpty())
    }

    @Test
    fun zipPathRules() {
        for (ok in listOf("website-project/index.html", "website-project/assets/images/صورة.png", "website-project/assets/")) {
            assertTrue(ZipPaths.isSafeEntryName(ok), ok)
        }
        for (bad in listOf("", "/a", "a/../b", "..", "a\\b", "C:/a", "a//b", "./a", "a/\u0000b")) {
            assertFalse(ZipPaths.isSafeEntryName(bad), bad)
        }
    }

    @Test
    fun videoAudioAndDownloadAssetsAreCopiedOrReportedMissing() {
        val d = doc(
            Element("v", ElementType.VIDEO, mapOf("src" to "assets/media/clip.mp4")),
            Element("a", ElementType.AUDIO, mapOf("src" to "assets/media/gone.mp3")),
            Element("dl", ElementType.DOWNLOAD_BUTTON, mapOf("text" to "نزّل", "href" to "assets/files/cv.pdf")),
            Element("dl2", ElementType.DOWNLOAD_BUTTON, mapOf("text" to "نزّل", "href" to "assets/files/gone.pdf")),
        )
        val assets = MapAssets(mapOf("assets/media/clip.mp4" to png, "assets/files/cv.pdf" to png))
        val (r, bytes) = exportBytes(d, assets)
        val success = assertIs<ExportResult.Success>(r)
        val zip = readZip(bytes)
        assertTrue("website-project/assets/media/clip.mp4" in zip.keys)
        assertTrue("website-project/assets/files/cv.pdf" in zip.keys)
        assertEquals(
            setOf(
                ExportWarning(ExportWarningCode.MISSING_ASSET, "assets/media/gone.mp3", "a"),
                ExportWarning(ExportWarningCode.LOCAL_LINK_TARGET_MISSING, "assets/files/gone.pdf", "dl2"),
            ),
            success.warnings.toSet(),
        )
    }
}

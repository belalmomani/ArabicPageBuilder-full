package com.arabicpagebuilder.core.export

import com.arabicpagebuilder.core.generator.HtmlGenerator
import com.arabicpagebuilder.core.model.PageDocument
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * يصدّر الموقع كاملًا إلى ZIP: index.html وcss/style.css والأصول المستخدمة فعلًا وREADME.txt،
 * داخل مجلد جذر website-project/. لا يعتمد على أندرويد.
 */
object SiteExporter {
    const val ROOT_DIR = "website-project"
    private const val FIXED_TIME_MILLIS = 1_577_836_800_000L // 2020-01-01 UTC: لمخرجات قابلة للتكرار.

    private class ExportAbort(val error: ExportError, val detail: String?) : Exception(detail)

    /**
     * يكتب ZIP إلى [out]. عند الفشل قد يكون [out] قد استلم بيانات جزئية، فعلى المستدعي تجاهلها.
     * لا يغلق [out]. لا يتحقق من الناتج؛ للتحقق استخدم [exportToFile].
     */
    fun export(
        doc: PageDocument,
        assets: AssetSource,
        out: OutputStream,
        limits: ExportLimits = ExportLimits(),
    ): ExportResult {
        val site = HtmlGenerator.generate(doc)

        // 1) تحديد ما سيُنسخ وما هو مفقود.
        val warnings = ArrayList<ExportWarning>()
        val toCopy = LinkedHashSet<String>()
        val checked = HashSet<String>()
        for (ref in LocalReferences.collect(doc)) {
            if (!checked.add("${ref.kind}:${ref.path}")) continue
            val usable = ref.path.startsWith("assets/") &&
                ZipPaths.isSafeEntryName("$ROOT_DIR/${ref.path}") &&
                assets.exists(ref.path)
            if (usable) {
                toCopy.add(ref.path)
            } else {
                val code = if (ref.kind == RefKind.IMAGE) ExportWarningCode.MISSING_ASSET else ExportWarningCode.LOCAL_LINK_TARGET_MISSING
                warnings.add(ExportWarning(code, ref.path, ref.elementId))
            }
        }

        val readme = ReadmeBuilder.build(doc.title, warnings, site.warnings)

        // 2) الكتابة.
        val entries = ArrayList<String>()
        try {
            val zip = ZipOutputStream(out)
            var total = 0L
            fun putBytes(name: String, text: String) {
                val bytes = text.toByteArray(Charsets.UTF_8)
                total += bytes.size
                if (total > limits.maxTotalBytes) throw ExportAbort(ExportError.TOTAL_TOO_LARGE, name)
                zip.putNextEntry(newEntry("$ROOT_DIR/$name"))
                zip.write(bytes)
                zip.closeEntry()
                entries.add("$ROOT_DIR/$name")
            }
            putBytes(HtmlGenerator.INDEX_PATH, site.indexHtml)
            putBytes(HtmlGenerator.CSS_PATH, site.styleCss)
            for (path in toCopy) {
                val input = assets.open(path) ?: throw ExportAbort(ExportError.IO_ERROR, "تعذر فتح الملف: $path")
                zip.putNextEntry(newEntry("$ROOT_DIR/$path"))
                total += input.use { copyLimited(it, zip, path, limits, total) }
                zip.closeEntry()
                entries.add("$ROOT_DIR/$path")
            }
            putBytes("README.txt", readme)
            zip.finish()
            out.flush()
        } catch (a: ExportAbort) {
            return ExportResult.Failure(a.error, a.detail)
        } catch (e: IOException) {
            return ExportResult.Failure(ExportError.IO_ERROR, e.message)
        }
        return ExportResult.Success(entries, warnings, site.warnings)
    }

    /**
     * يصدّر إلى ملف مؤقت بجوار [target]، ويتحقق من سلامته، ثم ينقله مكان [target].
     * عند أي فشل يُحذف المؤقت ويبقى [target] (إن وُجد) كما كان.
     */
    fun exportToFile(
        doc: PageDocument,
        assets: AssetSource,
        target: File,
        limits: ExportLimits = ExportLimits(),
    ): ExportResult {
        val dir = target.absoluteFile.parentFile
            ?: return ExportResult.Failure(ExportError.IO_ERROR, "لا يوجد مجلد وجهة")
        val tmp = try {
            File.createTempFile("export-", ".zip.tmp", dir)
        } catch (e: IOException) {
            return ExportResult.Failure(ExportError.IO_ERROR, e.message)
        }
        try {
            val result = tmp.outputStream().buffered().use { export(doc, assets, it, limits) }
            if (result is ExportResult.Failure) {
                tmp.delete()
                return result
            }
            val problems = ExportVerifier.verify(tmp)
            if (problems.isNotEmpty()) {
                tmp.delete()
                return ExportResult.Failure(ExportError.VERIFICATION_FAILED, problems.joinToString("; "))
            }
            moveInto(tmp, target)
            return result
        } catch (e: IOException) {
            tmp.delete()
            return ExportResult.Failure(ExportError.IO_ERROR, e.message)
        }
    }

    private fun newEntry(name: String): ZipEntry = ZipEntry(name).apply { time = FIXED_TIME_MILLIS }

    private fun copyLimited(input: InputStream, zip: ZipOutputStream, path: String, limits: ExportLimits, totalBefore: Long): Long {
        val buf = ByteArray(8192)
        var n = 0L
        while (true) {
            val r = input.read(buf)
            if (r < 0) break
            n += r
            if (n > limits.maxAssetBytes) throw ExportAbort(ExportError.ASSET_TOO_LARGE, path)
            if (totalBefore + n > limits.maxTotalBytes) throw ExportAbort(ExportError.TOTAL_TOO_LARGE, path)
            zip.write(buf, 0, r)
        }
        return n
    }

    private fun moveInto(tmp: File, target: File) {
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

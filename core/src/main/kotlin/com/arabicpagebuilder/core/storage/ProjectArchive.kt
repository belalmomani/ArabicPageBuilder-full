package com.arabicpagebuilder.core.storage

import com.arabicpagebuilder.core.export.ExportLimits
import com.arabicpagebuilder.core.export.LocalReferences
import com.arabicpagebuilder.core.export.RefKind
import com.arabicpagebuilder.core.export.ZipPaths
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportLimits(
    val maxEntries: Int = 2000,
    val maxEntryBytes: Long = 20L * 1024 * 1024,
    val maxTotalBytes: Long = 200L * 1024 * 1024,
)

/** [missingAssets]: ملفات تشير إليها الصفحة (صور) ولم تكن داخل الأرشيف. */
data class ImportedProject(val project: LoadedProject, val missingAssets: List<String>)

/**
 * أرشيف مشروع قابل للمشاركة (ZIP بامتداد .apbproj): project.json + assets/images|media/ فقط.
 * أي شيء آخر يُرفض. الاستيراد ينتج دائمًا مشروعًا بمعرّف جديد ولا يستبدل مشروعًا قائمًا.
 */
class ProjectArchive(private val store: ProjectStore) {

    companion object {
        const val PROJECT_ENTRY = "project.json"
        const val EXTENSION = "apbproj"
        private const val MAX_PROJECT_JSON_BYTES = 25L * 1024 * 1024
        private const val FIXED_TIME_MILLIS = 1_577_836_800_000L
    }

    private class Rejected(val error: StoreError, val detail: String) : Exception(detail)

    // ---- التصدير ----

    /** يكتب أرشيف المشروع إلى [out] (لا يغلقه). يُرجع عدد الملفات المكتوبة. */
    fun exportProject(id: String, out: OutputStream, limits: ExportLimits = ExportLimits()): StoreResult<Int> {
        val loaded = when (val l = store.load(id)) {
            is StoreResult.Err -> return l
            is StoreResult.Ok -> l.value
        }
        val dir = store.projectDir(id) ?: return StoreResult.Err(StoreError.INVALID_ID)
        try {
            val zip = ZipOutputStream(out)
            var total = 0L
            var count = 0
            val json = ProjectFormat.encode(loaded.data).toByteArray(Charsets.UTF_8)
            total += json.size
            zip.putNextEntry(newEntry(PROJECT_ENTRY))
            zip.write(json)
            zip.closeEntry()
            count++
            for (sub in listOf("images", "media", "files")) {
                val base = File(dir, "assets/$sub")
                val files = base.listFiles { f -> f.isFile }?.sortedBy { it.name } ?: continue
                for (f in files) {
                    if (!AssetNames.isValidStoredName(f.name, sub) || !isInside(f, base)) continue
                    zip.putNextEntry(newEntry("assets/$sub/${f.name}"))
                    total += f.inputStream().buffered().use { copyLimited(it, zip, f.name, limits, total) }
                    zip.closeEntry()
                    count++
                }
            }
            zip.finish()
            out.flush()
            return StoreResult.Ok(count)
        } catch (r: Rejected) {
            return StoreResult.Err(r.error, r.detail)
        } catch (e: IOException) {
            return StoreResult.Err(StoreError.IO_ERROR, e.message)
        }
    }

    /** يصدّر إلى ملف مؤقت ثم يتحقق منه ثم ينقله؛ عند الفشل يبقى [target] كما كان. */
    fun exportProjectToFile(id: String, target: File, limits: ExportLimits = ExportLimits()): StoreResult<Int> {
        val parent = target.absoluteFile.parentFile ?: return StoreResult.Err(StoreError.IO_ERROR, "لا يوجد مجلد وجهة")
        val tmp = try {
            File.createTempFile("archive-", ".tmp", parent)
        } catch (e: IOException) {
            return StoreResult.Err(StoreError.IO_ERROR, e.message)
        }
        try {
            val r = tmp.outputStream().buffered().use { exportProject(id, it, limits) }
            if (r is StoreResult.Err) return r
            val problems = verify(tmp)
            if (problems.isNotEmpty()) return StoreResult.Err(StoreError.INVALID_ARCHIVE, problems.joinToString("; "))
            store.moveReplace(tmp, target)
            return r
        } catch (e: IOException) {
            return StoreResult.Err(StoreError.IO_ERROR, e.message)
        } finally {
            tmp.delete()
        }
    }

    // ---- الاستيراد ----

    fun importProjectFile(file: File, limits: ImportLimits = ImportLimits()): StoreResult<ImportedProject> =
        try {
            file.inputStream().buffered().use { importProject(it, limits) }
        } catch (e: IOException) {
            StoreResult.Err(StoreError.IO_ERROR, e.message)
        }

    /**
     * يستورد أرشيفًا إلى مشروع جديد. يُفكّ داخل مجلد مؤقت داخل مجلد المخزن، ولا ينتقل إلى مكانه النهائي
     * إلا بعد اكتمال كل الفحوص. أي رفض يحذف المؤقت ولا يترك أثرًا. لا يغلق [input].
     */
    fun importProject(input: InputStream, limits: ImportLimits = ImportLimits()): StoreResult<ImportedProject> {
        store.root.mkdirs()
        val staging = File(store.root, ".import-" + UUID.randomUUID())
        try {
            if (!staging.mkdirs()) return StoreResult.Err(StoreError.IO_ERROR, "تعذر إنشاء مجلد مؤقت")
            val stagingCanonical = staging.canonicalFile
            val zis = ZipInputStream(input)
            val seen = HashSet<String>()
            var total = 0L
            var count = 0
            while (true) {
                val entry = zis.nextEntry ?: break
                if (++count > limits.maxEntries) throw Rejected(StoreError.ARCHIVE_TOO_LARGE, "عدد المدخلات أكبر من المسموح")
                val name = entry.name
                if (!ZipPaths.isSafeEntryName(name)) throw Rejected(StoreError.INVALID_ARCHIVE, "مسار مدخل غير آمن")
                if (entry.isDirectory) continue
                entryProblem(name)?.let { throw Rejected(StoreError.INVALID_ARCHIVE, it) }
                if (!seen.add(name.lowercase())) throw Rejected(StoreError.INVALID_ARCHIVE, "مدخل مكرر: $name")
                val target = File(staging, name)
                val canonical = target.canonicalFile
                if (!canonical.path.startsWith(stagingCanonical.path + File.separator)) {
                    throw Rejected(StoreError.INVALID_ARCHIVE, "مدخل خارج مجلد الوجهة")
                }
                target.parentFile?.mkdirs()
                val maxEntry = if (name == PROJECT_ENTRY) MAX_PROJECT_JSON_BYTES else limits.maxEntryBytes
                total += extract(zis, target, maxEntry, total, limits.maxTotalBytes)
            }

            val projectFile = File(staging, PROJECT_ENTRY)
            if (!projectFile.isFile) throw Rejected(StoreError.INVALID_ARCHIVE, "project.json غير موجود")
            val data = when (val d = store.readProjectFile(projectFile)) {
                is DecodeResult.Success -> d.data
                is DecodeResult.Failure -> throw Rejected(StoreError.INVALID_ARCHIVE, "project.json غير صالح: ${d.error}")
            }
            val missing = LocalReferences.collect(data.document)
                .filter { it.kind == RefKind.IMAGE }
                .map { it.path }
                .filter { !(it.startsWith("assets/") && File(staging, it).isFile) }
                .distinct()

            val newId = store.nextId()
            val dest = store.projectDir(newId) ?: throw Rejected(StoreError.INVALID_ID, "معرّف غير صالح")
            if (dest.exists()) throw Rejected(StoreError.ALREADY_EXISTS, newId)
            val newData = data.copy(modifiedAt = store.now())
            store.writeProject(staging, newData, rotateBackup = false)
            moveDirectory(staging, dest)
            return StoreResult.Ok(ImportedProject(LoadedProject(newId, newData), missing))
        } catch (r: Rejected) {
            return StoreResult.Err(r.error, r.detail)
        } catch (e: ZipException) {
            return StoreResult.Err(StoreError.INVALID_ARCHIVE, e.message)
        } catch (e: IllegalArgumentException) { // اسم مدخل بترميز غير صالح
            return StoreResult.Err(StoreError.INVALID_ARCHIVE, e.message)
        } catch (e: IOException) {
            return StoreResult.Err(StoreError.IO_ERROR, e.message)
        } finally {
            staging.deleteRecursively()
        }
    }

    // ---- التحقق ----

    /** يفحص أرشيفًا: يفتحه، ويقرأ كل مدخل (CRC)، ويتحقق من الأسماء ومن صلاحية project.json. فارغ = سليم. */
    fun verify(file: File): List<String> {
        val problems = ArrayList<String>()
        try {
            ZipFile(file).use { zf ->
                val seen = HashSet<String>()
                val buf = ByteArray(8192)
                var hasProject = false
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (!ZipPaths.isSafeEntryName(e.name)) {
                        problems.add("اسم مدخل غير آمن: ${e.name}")
                        continue
                    }
                    if (e.isDirectory) continue
                    entryProblem(e.name)?.let { problems.add(it) }
                    if (!seen.add(e.name.lowercase())) problems.add("مدخل مكرر: ${e.name}")
                    zf.getInputStream(e).use { s -> while (s.read(buf) >= 0) { /* قراءة كاملة لفحص CRC */ } }
                    if (e.name == PROJECT_ENTRY) hasProject = true
                }
                val entry = zf.getEntry(PROJECT_ENTRY)
                if (!hasProject || entry == null) {
                    problems.add("project.json غير موجود")
                } else if (entry.size > MAX_PROJECT_JSON_BYTES) {
                    problems.add("project.json كبير جدًا")
                } else {
                    val text = String(zf.getInputStream(entry).use { it.readBytes() }, Charsets.UTF_8)
                    val d = ProjectFormat.decode(text)
                    if (d is DecodeResult.Failure) problems.add("project.json غير صالح: ${d.error}")
                }
            }
        } catch (e: ZipException) {
            problems.add("ملف ZIP تالف: ${e.message}")
        } catch (e: IOException) {
            problems.add("تعذرت قراءة الملف: ${e.message}")
        }
        return problems
    }

    // ---- تفاصيل ----

    /** سبب رفض اسم مدخل غير مسموح، أو null. المسموح فقط: project.json وassets/{images|media}/<اسم ملف مقبول>. */
    private fun entryProblem(name: String): String? {
        if (name == PROJECT_ENTRY) return null
        val parts = name.split('/')
        if (parts.size == 3 && parts[0] == "assets" && (parts[1] == "images" || parts[1] == "media" || parts[1] == "files")) {
            return if (AssetNames.isValidStoredName(parts[2], parts[1])) null else "اسم ملف غير مقبول: $name"
        }
        return "مدخل غير متوقع: $name"
    }

    private fun extract(zis: ZipInputStream, target: File, maxEntry: Long, totalBefore: Long, maxTotal: Long): Long {
        var n = 0L
        val buf = ByteArray(8192)
        target.outputStream().buffered().use { out ->
            while (true) {
                val r = zis.read(buf)
                if (r < 0) break
                n += r
                // العدّ على البايتات المفكوكة فعلًا، لا على الحجم المعلن في الأرشيف.
                if (n > maxEntry) throw Rejected(StoreError.ARCHIVE_TOO_LARGE, "ملف داخل الأرشيف أكبر من المسموح")
                if (totalBefore + n > maxTotal) throw Rejected(StoreError.ARCHIVE_TOO_LARGE, "حجم الأرشيف بعد الفك أكبر من المسموح")
                out.write(buf, 0, r)
            }
        }
        return n
    }

    private fun copyLimited(input: InputStream, zip: ZipOutputStream, name: String, limits: ExportLimits, totalBefore: Long): Long {
        var n = 0L
        val buf = ByteArray(8192)
        while (true) {
            val r = input.read(buf)
            if (r < 0) break
            n += r
            if (n > limits.maxAssetBytes) throw Rejected(StoreError.ASSET_TOO_LARGE, name)
            if (totalBefore + n > limits.maxTotalBytes) throw Rejected(StoreError.ARCHIVE_TOO_LARGE, name)
            zip.write(buf, 0, r)
        }
        return n
    }

    private fun isInside(file: File, base: File): Boolean = try {
        file.canonicalFile.path.startsWith(base.canonicalFile.path + File.separator)
    } catch (e: IOException) {
        false
    }

    private fun newEntry(name: String): ZipEntry = ZipEntry(name).apply { time = FIXED_TIME_MILLIS }

    private fun moveDirectory(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath())
        }
    }
}

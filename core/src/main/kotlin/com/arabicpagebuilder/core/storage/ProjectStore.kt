package com.arabicpagebuilder.core.storage

import com.arabicpagebuilder.core.export.AssetSource
import com.arabicpagebuilder.core.export.ZipPaths
import com.arabicpagebuilder.core.generator.UrlSanitizer
import com.arabicpagebuilder.core.model.IdGenerator
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.TextDirection
import com.arabicpagebuilder.core.model.UuidIdGenerator
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * التخزين المحلي للمشاريع: مجلد لكل مشروع تحت [root]
 *   <id>/project.json  و  <id>/project.json.bak (آخر نسخة سابقة سليمة)  و  <id>/assets/...
 * كل كتابة تتم إلى ملف مؤقت ثم يُتحقق منها ثم تُنقل ذريًا، فلا يتلف المشروع عند الفشل.
 */
class ProjectStore(
    internal val root: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ids: IdGenerator = UuidIdGenerator,
) {
    companion object {
        const val PROJECT_FILE = "project.json"
        const val BACKUP_FILE = "project.json.bak"
        private const val TMP_FILE = "project.json.tmp"
        private const val MAX_FILE_BYTES = 25L * 1024 * 1024
        private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")
    }

    // ---- إدارة المشاريع ----

    fun create(name: String, title: String, direction: TextDirection = TextDirection.RTL): StoreResult<LoadedProject> = io {
        val clean = cleanName(name) ?: return@io StoreResult.Err(StoreError.INVALID_NAME)
        val id = ids.next()
        val dir = projectDir(id) ?: return@io StoreResult.Err(StoreError.INVALID_ID)
        if (dir.exists()) return@io StoreResult.Err(StoreError.ALREADY_EXISTS)
        if (!dir.mkdirs()) return@io StoreResult.Err(StoreError.IO_ERROR, "تعذر إنشاء المجلد")
        val now = clock()
        val data = ProjectData(clean, now, now, PageDocument.empty(title, direction, ids))
        try {
            writeProject(dir, data, rotateBackup = false)
        } catch (e: IOException) {
            dir.deleteRecursively()
            throw e
        }
        StoreResult.Ok(LoadedProject(id, data))
    }

    fun load(id: String): StoreResult<LoadedProject> = io {
        val dir = projectDir(id) ?: return@io StoreResult.Err(StoreError.INVALID_ID)
        val file = File(dir, PROJECT_FILE)
        if (!file.isFile) return@io StoreResult.Err(StoreError.NOT_FOUND)
        when (val r = readFile(file)) {
            is DecodeResult.Success -> StoreResult.Ok(LoadedProject(id, r.data))
            is DecodeResult.Failure -> StoreResult.Err(StoreError.CORRUPT, r.error.name)
        }
    }

    /** يحفظ المستند. إن لم يتغير عن المحفوظ لا يُكتب شيء (فتبقى النسخة الاحتياطية كما هي). */
    fun save(id: String, document: PageDocument): StoreResult<LoadedProject> = io {
        val dir = projectDir(id) ?: return@io StoreResult.Err(StoreError.INVALID_ID)
        if (!dir.isDirectory) return@io StoreResult.Err(StoreError.NOT_FOUND)
        val existing = (readFile(File(dir, PROJECT_FILE)) as? DecodeResult.Success)?.data
        if (existing != null && existing.document == document) {
            return@io StoreResult.Ok(LoadedProject(id, existing))
        }
        val data = ProjectData(
            name = existing?.name ?: document.title.trim().ifEmpty { "مشروع" }.take(ProjectFormat.MAX_NAME_LENGTH),
            createdAt = existing?.createdAt ?: clock(),
            modifiedAt = clock(),
            document = document,
        )
        writeProject(dir, data, rotateBackup = true)
        StoreResult.Ok(LoadedProject(id, data))
    }

    /** يستعيد آخر نسخة سابقة محفوظة (مفيد أيضًا إن تلف الملف الحالي). */
    fun restoreBackup(id: String): StoreResult<LoadedProject> = io {
        val dir = projectDir(id) ?: return@io StoreResult.Err(StoreError.INVALID_ID)
        if (!dir.isDirectory) return@io StoreResult.Err(StoreError.NOT_FOUND)
        val bak = File(dir, BACKUP_FILE)
        if (!bak.isFile) return@io StoreResult.Err(StoreError.NO_BACKUP)
        when (val r = readFile(bak)) {
            is DecodeResult.Failure -> StoreResult.Err(StoreError.CORRUPT, r.error.name)
            is DecodeResult.Success -> {
                val data = r.data.copy(modifiedAt = clock())
                writeProject(dir, data, rotateBackup = false)
                StoreResult.Ok(LoadedProject(id, data))
            }
        }
    }

    fun rename(id: String, newName: String): StoreResult<LoadedProject> = io {
        val clean = cleanName(newName) ?: return@io StoreResult.Err(StoreError.INVALID_NAME)
        when (val loaded = load(id)) {
            is StoreResult.Err -> loaded
            is StoreResult.Ok -> {
                val data = loaded.value.data.copy(name = clean, modifiedAt = clock())
                writeProject(File(root, id), data, rotateBackup = false)
                StoreResult.Ok(LoadedProject(id, data))
            }
        }
    }

    /** ينسخ المشروع كاملًا (مع الأصول) باسم جديد ومعرّف جديد. */
    fun duplicate(id: String): StoreResult<LoadedProject> = io {
        val loaded = when (val l = load(id)) {
            is StoreResult.Err -> return@io l
            is StoreResult.Ok -> l.value
        }
        val newId = ids.next()
        val dst = projectDir(newId) ?: return@io StoreResult.Err(StoreError.INVALID_ID)
        if (dst.exists()) return@io StoreResult.Err(StoreError.ALREADY_EXISTS)
        if (!dst.mkdirs()) return@io StoreResult.Err(StoreError.IO_ERROR, "تعذر إنشاء المجلد")
        val now = clock()
        val data = loaded.data.copy(
            name = "${loaded.data.name} (نسخة)".take(ProjectFormat.MAX_NAME_LENGTH),
            createdAt = now,
            modifiedAt = now,
        )
        try {
            val assets = File(File(root, id), "assets")
            if (assets.isDirectory) assets.copyRecursively(File(dst, "assets"), overwrite = false)
            writeProject(dst, data, rotateBackup = false)
        } catch (e: IOException) {
            dst.deleteRecursively()
            throw e
        }
        StoreResult.Ok(LoadedProject(newId, data))
    }

    /** يحذف المشروع نهائيًا. التأكيد من المستخدم مسؤولية الواجهة. */
    fun delete(id: String): StoreResult<Unit> = io {
        val dir = projectDir(id) ?: return@io StoreResult.Err(StoreError.INVALID_ID)
        if (!dir.isDirectory) return@io StoreResult.Err(StoreError.NOT_FOUND)
        if (!dir.deleteRecursively()) return@io StoreResult.Err(StoreError.IO_ERROR, "تعذر الحذف الكامل")
        StoreResult.Ok(Unit)
    }

    /** المشاريع الأحدث تعديلًا أولًا؛ التالفة في النهاية مع إمكانية استعادتها إن وُجدت نسخة. */
    fun list(): List<ProjectSummary> {
        val dirs = root.listFiles { f -> f.isDirectory && ID_PATTERN.matches(f.name) } ?: return emptyList()
        val all = dirs.map { d ->
            val hasBackup = File(d, BACKUP_FILE).isFile
            val ok = try {
                (readFile(File(d, PROJECT_FILE)) as? DecodeResult.Success)?.data
            } catch (e: IOException) {
                null
            }
            if (ok != null) {
                ProjectSummary(d.name, ok.name, ok.document.title, ok.createdAt, ok.modifiedAt, corrupt = false, hasBackup = hasBackup)
            } else {
                ProjectSummary(d.name, d.name, "", 0L, 0L, corrupt = true, hasBackup = hasBackup)
            }
        }
        return all.sortedWith(compareBy<ProjectSummary> { it.corrupt }.thenByDescending { it.modifiedAt }.thenBy { it.name })
    }

    /** يحذف مجلدات الاستيراد المؤقتة (.import-*) التي تركها انقطاع مفاجئ وأقدم من [olderThanMillis]. يُرجع عددها. */
    fun cleanupStaleTemp(olderThanMillis: Long = 60L * 60 * 1000): Int {
        val limit = clock() - olderThanMillis
        val stale = root.listFiles { f -> f.isDirectory && f.name.startsWith(".import-") && f.lastModified() < limit } ?: return 0
        return stale.count { it.deleteRecursively() }
    }

    // ---- الأصول ----

    /** مصدر أصول المشروع للمصدّر؛ لا يعطي إلا ملفات داخل assets/ وبعد فحص المسار. */
    fun assetSource(id: String): AssetSource? {
        val dir = projectDir(id)?.takeIf { it.isDirectory } ?: return null
        return ProjectAssets(dir)
    }

    /** يضيف ملفًا إلى assets بعد تنظيف اسمه. يُرجع المسار النسبي مثل assets/images/photo.png. */
    fun addAsset(id: String, originalName: String, input: InputStream, maxBytes: Long = 20L * 1024 * 1024): StoreResult<String> = io {
        val dir = projectDir(id) ?: return@io StoreResult.Err(StoreError.INVALID_ID)
        if (!dir.isDirectory) return@io StoreResult.Err(StoreError.NOT_FOUND)
        val parsed = AssetNames.parse(originalName) ?: return@io StoreResult.Err(StoreError.ASSET_NOT_ALLOWED, originalName)
        val sub = AssetNames.folderFor(parsed.ext) ?: return@io StoreResult.Err(StoreError.ASSET_NOT_ALLOWED, originalName)
        val targetDir = File(dir, "assets/$sub")
        if (!targetDir.isDirectory && !targetDir.mkdirs()) return@io StoreResult.Err(StoreError.IO_ERROR, "تعذر إنشاء مجلد الأصول")
        var candidate = "${parsed.base}.${parsed.ext}"
        var n = 2
        while (File(targetDir, candidate).exists()) {
            candidate = "${parsed.base}-$n.${parsed.ext}"
            n++
        }
        val tmp = File.createTempFile("asset-", ".tmp", targetDir)
        try {
            if (!copyLimited(input, tmp, maxBytes)) return@io StoreResult.Err(StoreError.ASSET_TOO_LARGE, originalName)
            moveReplace(tmp, File(targetDir, candidate))
            StoreResult.Ok("assets/$sub/$candidate")
        } finally {
            tmp.delete()
        }
    }

    /** للاستخدام الداخلي (الأرشفة): ساعة المخزن ومولّد معرّفاته. */
    internal fun now(): Long = clock()

    internal fun nextId(): String = ids.next()

    // ---- تفاصيل داخلية ----

    private class ProjectAssets(private val projectDir: File) : AssetSource {
        private fun resolve(path: String): File? {
            if (!path.startsWith("assets/")) return null
            if (UrlSanitizer.sanitizeLocalPath(path) == null || !ZipPaths.isSafeEntryName(path)) return null
            return try {
                val base = File(projectDir, "assets").canonicalFile
                val f = File(projectDir, path).canonicalFile
                if (f.path.startsWith(base.path + File.separator) && f.isFile) f else null
            } catch (e: IOException) {
                null
            }
        }

        override fun exists(path: String): Boolean = resolve(path) != null

        override fun open(path: String): InputStream? = resolve(path)?.inputStream()?.buffered()
    }

    private inline fun <T> io(block: () -> StoreResult<T>): StoreResult<T> =
        try {
            block()
        } catch (e: IOException) {
            StoreResult.Err(StoreError.IO_ERROR, e.message)
        }

    internal fun projectDir(id: String): File? = if (ID_PATTERN.matches(id)) File(root, id) else null

    private fun cleanName(name: String): String? {
        val t = name.trim()
        if (t.isEmpty() || t.length > ProjectFormat.MAX_NAME_LENGTH || t.any { it.isISOControl() }) return null
        return t
    }

    internal fun readProjectFile(file: File): DecodeResult = readFile(file)

    private fun readFile(file: File): DecodeResult {
        if (!file.isFile) return DecodeResult.Failure(FormatError.MALFORMED_JSON, "الملف غير موجود")
        if (file.length() > MAX_FILE_BYTES) return DecodeResult.Failure(FormatError.TOO_LARGE, "${file.length()}")
        return ProjectFormat.decode(file.readText(Charsets.UTF_8))
    }

    /** كتابة آمنة: مؤقت ← تحقق ← (نسخة احتياطية للسليم الحالي) ← نقل ذري. */
    internal fun writeProject(dir: File, data: ProjectData, rotateBackup: Boolean) {
        val json = ProjectFormat.encode(data)
        val tmp = File(dir, TMP_FILE)
        val bakTmp = File(dir, "$BACKUP_FILE.tmp")
        try {
            tmp.writeText(json, Charsets.UTF_8)
            if (readFile(tmp) !is DecodeResult.Success) throw IOException("فشل التحقق من الملف المكتوب")
            val current = File(dir, PROJECT_FILE)
            if (rotateBackup && readFile(current) is DecodeResult.Success) {
                Files.copy(current.toPath(), bakTmp.toPath(), StandardCopyOption.REPLACE_EXISTING)
                moveReplace(bakTmp, File(dir, BACKUP_FILE))
            }
            moveReplace(tmp, current)
        } finally {
            tmp.delete()
            bakTmp.delete()
        }
    }

    private fun copyLimited(input: InputStream, target: File, maxBytes: Long): Boolean {
        var total = 0L
        val buf = ByteArray(8192)
        target.outputStream().buffered().use { out ->
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                total += r
                if (total > maxBytes) return false
                out.write(buf, 0, r)
            }
        }
        return true
    }

    internal fun moveReplace(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

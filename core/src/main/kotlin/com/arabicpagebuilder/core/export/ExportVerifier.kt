package com.arabicpagebuilder.core.export

import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** يفحص ZIP مُصدَّرًا: يفتح، ويقرأ كل مدخل (فيُفحص CRC)، ويتحقق من الأسماء ومن وجود الملفات المشار إليها. */
object ExportVerifier {

    private val ref = Regex("(?:src|href)=\"([^\"]*)\"")

    /** قائمة المشكلات؛ فارغة تعني أن الملف سليم. */
    fun verify(file: File): List<String> {
        val problems = ArrayList<String>()
        try {
            ZipFile(file).use { zf ->
                val names = HashSet<String>()
                val buf = ByteArray(8192)
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (!ZipPaths.isSafeEntryName(e.name)) problems.add("اسم مدخل غير آمن: ${e.name}")
                    if (!e.name.startsWith(SiteExporter.ROOT_DIR + "/")) problems.add("مدخل خارج المجلد الجذر: ${e.name}")
                    if (e.isDirectory) continue
                    names.add(e.name)
                    zf.getInputStream(e).use { s -> while (s.read(buf) >= 0) { /* قراءة كاملة لفحص CRC */ } }
                }
                val indexName = "${SiteExporter.ROOT_DIR}/index.html"
                val indexEntry = zf.getEntry(indexName)
                if (indexEntry == null) {
                    problems.add("index.html غير موجود")
                } else {
                    val html = zf.getInputStream(indexEntry).use { String(it.readFully(), Charsets.UTF_8) }
                    for (m in ref.findAll(html)) {
                        val target = localTarget(m.groupValues[1]) ?: continue
                        if ("${SiteExporter.ROOT_DIR}/$target" !in names) problems.add("ملف مشار إليه غير موجود: $target")
                    }
                }
            }
        } catch (e: ZipException) {
            problems.add("ملف ZIP تالف: ${e.message}")
        } catch (e: IOException) {
            problems.add("تعذرت قراءة الملف: ${e.message}")
        }
        return problems
    }

    /** المسار المحلي المشار إليه، أو null للروابط الخارجية والمراسي. */
    private fun localTarget(rawAttr: String): String? {
        val v = rawAttr.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").trim()
        if (v.isEmpty() || v.startsWith("#") || v.startsWith("//") || v.contains(':')) return null
        return v.substringBefore('?').substringBefore('#').ifEmpty { null }
    }

    private fun java.io.InputStream.readFully(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val r = read(buf)
            if (r < 0) break
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }
}

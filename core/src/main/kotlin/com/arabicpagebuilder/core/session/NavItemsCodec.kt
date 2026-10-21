package com.arabicpagebuilder.core.session

import com.arabicpagebuilder.core.generator.UrlSanitizer

/** سطر واحد في قائمة التنقل: النص والرابط، وهل هو عنصر فرعي تحت العنصر الرئيسي السابق. */
data class NavEntry(val text: String, val href: String, val isSub: Boolean = false)

/**
 * يحوّل نص عناصر القائمة (سطر لكل عنصر: «النص|الرابط»، وسطر يبدأ بـ «- » عنصر فرعي) من وإلى قائمة منظمة،
 * مع عمليات التحرير (إضافة/حذف/نقل/تعديل). المحرر المرئي يعتمد عليه، والمولّد يقرأ النص نفسه فلا ازدواج في الصيغة.
 */
object NavItemsCodec {
    const val DEFAULT_TEXT = "عنصر جديد"
    const val MAX_ENTRIES = 40

    fun parse(raw: String): List<NavEntry> {
        val out = ArrayList<NavEntry>()
        for (line in raw.replace("\r\n", "\n").split('\n')) {
            val t = line.trim()
            if (t.isEmpty() || t == "-") continue
            val sub = t.startsWith("- ")
            val body = if (sub) t.removePrefix("- ").trim() else t
            val text = clean(body.substringBefore('|'))
            if (text.isEmpty()) continue
            val href = if (body.contains('|')) cleanHref(body.substringAfter('|')) else ""
            // العنصر الأول لا يكون فرعيًا (لا أب له).
            out.add(NavEntry(text, href, isSub = sub && out.isNotEmpty()))
        }
        return out
    }

    fun serialize(entries: List<NavEntry>): String =
        entries.mapIndexed { i, e ->
            val href = cleanHref(e.href).ifEmpty { "#" }
            val prefix = if (e.isSub && i > 0) "- " else ""
            prefix + clean(e.text) + "|" + href
        }.joinToString("\n")

    fun add(entries: List<NavEntry>, text: String = DEFAULT_TEXT, href: String = "#", isSub: Boolean = false): List<NavEntry> {
        if (entries.size >= MAX_ENTRIES) return entries
        return entries + NavEntry(clean(text).ifEmpty { DEFAULT_TEXT }, cleanHref(href).ifEmpty { "#" }, isSub && entries.isNotEmpty())
    }

    fun remove(entries: List<NavEntry>, index: Int): List<NavEntry> {
        if (index !in entries.indices) return entries
        return fixFirst(entries.filterIndexed { i, _ -> i != index })
    }

    /** يبدّل العنصر مع جاره (اتجاه -1 للأعلى، +1 للأسفل). خارج الحدود لا يفعل شيئًا. */
    fun move(entries: List<NavEntry>, index: Int, direction: Int): List<NavEntry> {
        val to = index + direction
        if (index !in entries.indices || to !in entries.indices) return entries
        val list = entries.toMutableList()
        val moved = list.removeAt(index)
        list.add(to, moved)
        return fixFirst(list)
    }

    fun update(entries: List<NavEntry>, index: Int, text: String? = null, href: String? = null, isSub: Boolean? = null): List<NavEntry> {
        if (index !in entries.indices) return entries
        val e = entries[index]
        val updated = e.copy(
            text = text?.let { clean(it) } ?: e.text,
            href = href?.let { cleanHref(it) } ?: e.href,
            isSub = isSub ?: e.isSub,
        )
        return fixFirst(entries.toMutableList().also { it[index] = updated })
    }

    /** رسالة خطأ عربية إن كان الرابط غير صالح (الفارغ و# مقبولان: يعنيان أعلى الصفحة). */
    fun hrefError(href: String): String? {
        val h = href.trim()
        if (h.isEmpty() || h == "#") return null
        return if (UrlSanitizer.sanitizeLink(h) == null) "رابط غير صالح (المسموح: https وmailto وtel والمراسي والمسارات النسبية)" else null
    }

    // ---- تفاصيل ----

    private fun clean(s: String): String = s.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim()

    private fun cleanHref(s: String): String = s.replace('\n', ' ').replace('\r', ' ').trim()

    private fun fixFirst(entries: List<NavEntry>): List<NavEntry> =
        if (entries.isNotEmpty() && entries[0].isSub) listOf(entries[0].copy(isSub = false)) + entries.drop(1) else entries
}

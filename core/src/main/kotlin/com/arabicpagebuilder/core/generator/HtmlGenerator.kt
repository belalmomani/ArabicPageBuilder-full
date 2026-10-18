package com.arabicpagebuilder.core.generator

import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.TextDirection
import com.arabicpagebuilder.core.model.subtree

/**
 * يولّد index.html وcss/style.css من نموذج التصميم. دالة نقية: نفس المستند ينتج نفس الملفات حرفيًا.
 */
object HtmlGenerator {
    const val INDEX_PATH = "index.html"
    const val CSS_PATH = "css/style.css"

    private val BASE_CSS = """
        |*, *::before, *::after { box-sizing: border-box; }
        |body { margin: 0; font-family: system-ui, -apple-system, "Segoe UI", Tahoma, Arial, sans-serif; line-height: 1.6; }
        |img { max-width: 100%; height: auto; }
        |.row { display: flex; flex-wrap: wrap; gap: 1rem; }
        |.column { flex: 1 1 240px; min-width: 0; }
        |.card { border: 1px solid #e0e0e0; border-radius: 8px; padding: 1rem; }
        |.btn { display: inline-block; padding: .5rem 1rem; border: 0; border-radius: 6px; background: #1a73e8; color: #fff; text-decoration: none; font: inherit; }
        |.btn[disabled] { opacity: .5; }
    """.trimMargin()

    fun generate(doc: PageDocument): GeneratedSite {
        val warnings = ArrayList<GenerationWarning>()
        val body = StringBuilder()
        for (child in doc.root.children) render(child, 2, body, warnings)

        val dir = if (doc.direction == TextDirection.RTL) "rtl" else "ltr"
        val lang = if (doc.direction == TextDirection.RTL) "ar" else "en"
        val title = doc.title.ifBlank { "صفحة" }

        val html = buildString {
            append("<!DOCTYPE html>\n")
            append("<html lang=\"$lang\" dir=\"$dir\">\n")
            append("<head>\n")
            append("  <meta charset=\"utf-8\">\n")
            append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
            append("  <title>${HtmlEscape.text(title)}</title>\n")
            append("  <link rel=\"stylesheet\" href=\"css/style.css\">\n")
            append("</head>\n")
            append("<body class=\"${className(doc.root)}\">\n")
            append(body)
            append("</body>\n")
            append("</html>\n")
        }

        val css = buildCss(doc, warnings)
        return GeneratedSite(
            files = linkedMapOf(INDEX_PATH to html, CSS_PATH to css),
            warnings = warnings,
        )
    }

    /** اسم صنف ثابت مشتق من معرّف العنصر (أحرف آمنة فقط). */
    fun className(e: Element): String = "e-" + e.id.map { if (it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_') it else '_' }.joinToString("")

    private fun buildCss(doc: PageDocument, warnings: MutableList<GenerationWarning>): String = buildString {
        append(BASE_CSS).append('\n')
        for (node in doc.root.subtree()) {
            val result = CssSanitizer.convert(node.props)
            for (key in result.rejectedKeys) {
                warnings.add(GenerationWarning(node.id, WarningCode.INVALID_STYLE_VALUE, key))
            }
            if (result.declarations.isEmpty()) continue
            val selector = if (node.type == ElementType.PAGE_ROOT) "body.${className(node)}" else ".${className(node)}"
            append('\n').append(selector).append(" {\n")
            for (d in result.declarations) append("  ").append(d).append(";\n")
            append("}\n")
        }
    }

    private fun render(e: Element, depth: Int, out: StringBuilder, warnings: MutableList<GenerationWarning>) {
        val pad = "  ".repeat(depth)
        val cls = className(e)
        val text = e.props["text"].orEmpty()
        when (e.type) {
            ElementType.PAGE_ROOT -> Unit // الجذر لا يُعرض كعلامة؛ أبناؤه يُعرضون داخل body.
            ElementType.CONTAINER -> block("div", cls, e, depth, out, warnings)
            ElementType.SECTION -> block("section", cls, e, depth, out, warnings)
            ElementType.ROW -> block("div", "$cls row", e, depth, out, warnings)
            ElementType.COLUMN -> block("div", "$cls column", e, depth, out, warnings)
            ElementType.CARD -> block("div", "$cls card", e, depth, out, warnings)
            ElementType.HEADING -> {
                val level = e.props["level"]?.trim()?.toIntOrNull()?.coerceIn(1, 3) ?: 1
                out.append("$pad<h$level class=\"$cls\">${inline(text)}</h$level>\n")
            }
            ElementType.PARAGRAPH -> out.append("$pad<p class=\"$cls\">${inline(text)}</p>\n")
            ElementType.LINK -> {
                val href = checkedLink(e, warnings)
                if (href == null) {
                    out.append("$pad<span class=\"$cls\">${inline(text)}</span>\n")
                } else {
                    out.append("$pad<a class=\"$cls\" href=\"${HtmlEscape.attr(href)}\"${relFor(href)}>${inline(text)}</a>\n")
                }
            }
            ElementType.BUTTON -> {
                val href = checkedLink(e, warnings)
                if (href == null) {
                    // لا يوجد إجراء: نعرض الزر معطّلًا كي لا يوحي بأنه يعمل.
                    if (e.props["href"].isNullOrBlank()) {
                        warnings.add(GenerationWarning(e.id, WarningCode.BUTTON_WITHOUT_ACTION))
                    }
                    out.append("$pad<button type=\"button\" class=\"$cls btn\" disabled>${inline(text)}</button>\n")
                } else {
                    out.append("$pad<a class=\"$cls btn\" href=\"${HtmlEscape.attr(href)}\"${relFor(href)}>${inline(text)}</a>\n")
                }
            }
            ElementType.IMAGE -> renderImage(e, cls, pad, out, warnings)
        }
    }

    private fun block(
        tag: String,
        cls: String,
        e: Element,
        depth: Int,
        out: StringBuilder,
        warnings: MutableList<GenerationWarning>,
    ) {
        val pad = "  ".repeat(depth)
        if (e.children.isEmpty()) {
            out.append("$pad<$tag class=\"$cls\"></$tag>\n")
            return
        }
        out.append("$pad<$tag class=\"$cls\">\n")
        for (child in e.children) render(child, depth + 1, out, warnings)
        out.append("$pad</$tag>\n")
    }

    private fun renderImage(e: Element, cls: String, pad: String, out: StringBuilder, warnings: MutableList<GenerationWarning>) {
        val rawSrc = e.props["src"]
        if (rawSrc.isNullOrBlank()) {
            warnings.add(GenerationWarning(e.id, WarningCode.MISSING_IMAGE_SOURCE))
            return
        }
        val src = UrlSanitizer.sanitizeImageSource(rawSrc)
        if (src == null) {
            warnings.add(GenerationWarning(e.id, WarningCode.INVALID_IMAGE_SOURCE, rawSrc))
            return
        }
        val alt = e.props["alt"].orEmpty()
        if (alt.isBlank()) warnings.add(GenerationWarning(e.id, WarningCode.MISSING_ALT))
        out.append("$pad<img class=\"$cls\" src=\"${HtmlEscape.attr(src)}\" alt=\"${HtmlEscape.attr(alt)}\" loading=\"lazy\">\n")
    }

    private fun checkedLink(e: Element, warnings: MutableList<GenerationWarning>): String? {
        val raw = e.props["href"]
        if (raw.isNullOrBlank()) return null
        val ok = UrlSanitizer.sanitizeLink(raw)
        if (ok == null) warnings.add(GenerationWarning(e.id, WarningCode.INVALID_LINK, raw))
        return ok
    }

    private fun relFor(href: String): String =
        if (href.startsWith("http://", ignoreCase = true) || href.startsWith("https://", ignoreCase = true)) {
            " rel=\"noopener noreferrer\""
        } else {
            ""
        }

    /** نص بين الوسوم: ترميز ثم تحويل أسطر جديدة إلى <br>. */
    private fun inline(text: String): String =
        HtmlEscape.text(text.replace("\r\n", "\n").replace('\r', '\n')).replace("\n", "<br>")
}

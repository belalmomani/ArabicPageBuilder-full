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

    private val FIELD_NAME = Regex("^[A-Za-z][A-Za-z0-9_-]{0,39}$")

    private val BASE_CSS = """
        |*, *::before, *::after { box-sizing: border-box; }
        |body { margin: 0; font-family: system-ui, -apple-system, "Segoe UI", Tahoma, Arial, sans-serif; line-height: 1.6; }
        |img, video, audio { max-width: 100%; }
        |img, video { height: auto; }
        |hr { border: 0; border-top: 1px solid #dddddd; margin: 1rem 0; }
        |blockquote { margin: 1rem 0; padding: .5rem 1rem; border-inline-start: 4px solid #cccccc; }
        |blockquote footer { font-size: .9em; opacity: .8; }
        |.row { display: flex; flex-wrap: wrap; gap: 1rem; }
        |.column { flex: 1 1 240px; min-width: 0; }
        |.card { border: 1px solid #e0e0e0; border-radius: 8px; padding: 1rem; }
        |.grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1rem; }
        |.gallery { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: .5rem; }
        |.spacer { height: 24px; }
        |.btn { display: inline-block; padding: .5rem 1rem; border: 0; border-radius: 6px; background: #1a73e8; color: #fff; text-decoration: none; font: inherit; cursor: pointer; }
        |.btn[disabled] { opacity: .5; cursor: default; }
        |.form { display: flex; flex-direction: column; gap: .75rem; }
        |.field { display: flex; flex-direction: column; gap: .25rem; }
        |.field.check { flex-direction: row; align-items: center; gap: .5rem; }
        |.field input:not([type="checkbox"]), .field textarea, .field select { padding: .5rem; font: inherit; border: 1px solid #bbbbbb; border-radius: 6px; }
        |@media (max-width: 600px) { .grid { grid-template-columns: 1fr; } }
    """.trimMargin()

    fun generate(doc: PageDocument): GeneratedSite {
        val warnings = ArrayList<GenerationWarning>()
        val body = StringBuilder()
        for (child in doc.root.children) render(child, 2, body, warnings, inForm = false)

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
        val mobileRules = ArrayList<Pair<String, List<String>>>()
        val desktopHidden = ArrayList<String>()
        for (node in doc.root.subtree()) {
            val selector = if (node.type == ElementType.PAGE_ROOT) "body.${className(node)}" else ".${className(node)}"
            val result = CssSanitizer.convert(node.props)
            for (key in result.rejectedKeys) {
                warnings.add(GenerationWarning(node.id, WarningCode.INVALID_STYLE_VALUE, key))
            }
            if (result.declarations.isNotEmpty()) {
                append('\n').append(selector).append(" {\n")
                for (d in result.declarations) append("  ").append(d).append(";\n")
                append("}\n")
            }
            val mobile = CssSanitizer.convertMobile(node.props)
            for (key in mobile.rejectedKeys) {
                warnings.add(GenerationWarning(node.id, WarningCode.INVALID_STYLE_VALUE, key))
            }
            if (mobile.declarations.isNotEmpty()) mobileRules.add(selector to mobile.declarations)
            if (CssSanitizer.hiddenOnDesktop(node.props)) desktopHidden.add(selector)
        }
        if (mobileRules.isNotEmpty()) {
            append("\n@media (max-width: 600px) {\n")
            for ((selector, declarations) in mobileRules) {
                append("  ").append(selector).append(" {\n")
                for (d in declarations) append("    ").append(d).append(";\n")
                append("  }\n")
            }
            append("}\n")
        }
        if (desktopHidden.isNotEmpty()) {
            append("\n@media (min-width: 601px) {\n")
            for (selector in desktopHidden) append("  ").append(selector).append(" {\n    display: none;\n  }\n")
            append("}\n")
        }
    }

    private fun render(e: Element, depth: Int, out: StringBuilder, w: MutableList<GenerationWarning>, inForm: Boolean) {
        val pad = "  ".repeat(depth)
        val cls = className(e)
        val text = e.props["text"].orEmpty()
        when (e.type) {
            ElementType.PAGE_ROOT -> Unit // الجذر لا يُعرض كعلامة؛ أبناؤه يُعرضون داخل body.

            // ---- التخطيط ----
            ElementType.CONTAINER -> block("div", cls, e, depth, out, w, inForm)
            ElementType.SECTION -> block("section", cls, e, depth, out, w, inForm)
            ElementType.HEADER -> block("header", cls, e, depth, out, w, inForm)
            ElementType.FOOTER -> block("footer", cls, e, depth, out, w, inForm)
            ElementType.ROW -> block("div", "$cls row", e, depth, out, w, inForm)
            ElementType.COLUMN -> block("div", "$cls column", e, depth, out, w, inForm)
            ElementType.CARD -> block("div", "$cls card", e, depth, out, w, inForm)
            ElementType.GRID -> block("div", "$cls grid", e, depth, out, w, inForm)
            ElementType.GALLERY -> block("div", "$cls gallery", e, depth, out, w, inForm)
            ElementType.SPACER -> out.append("$pad<div class=\"$cls spacer\" aria-hidden=\"true\"></div>\n")

            // ---- النصوص ----
            ElementType.HEADING -> {
                val level = e.props["level"]?.trim()?.toIntOrNull()?.coerceIn(1, 3) ?: 1
                out.append("$pad<h$level class=\"$cls\">${inline(text)}</h$level>\n")
            }
            ElementType.PARAGRAPH -> out.append("$pad<p class=\"$cls\">${inline(text)}</p>\n")
            ElementType.TEXT -> out.append("$pad<span class=\"$cls\">${inline(text)}</span>\n")
            ElementType.BOLD -> out.append("$pad<strong class=\"$cls\">${inline(text)}</strong>\n")
            ElementType.ITALIC -> out.append("$pad<em class=\"$cls\">${inline(text)}</em>\n")
            ElementType.UNDERLINE -> out.append("$pad<u class=\"$cls\">${inline(text)}</u>\n")
            ElementType.LIST_ORDERED -> renderList("ol", cls, e, pad, out)
            ElementType.LIST_UNORDERED -> renderList("ul", cls, e, pad, out)
            ElementType.QUOTE -> {
                out.append("$pad<blockquote class=\"$cls\">\n")
                out.append("$pad  <p>${inline(text)}</p>\n")
                val cite = e.props["cite"].orEmpty()
                if (cite.isNotBlank()) out.append("$pad  <footer>${inline(cite)}</footer>\n")
                out.append("$pad</blockquote>\n")
            }
            ElementType.DIVIDER -> out.append("$pad<hr class=\"$cls\">\n")

            // ---- الوسائط ----
            ElementType.IMAGE -> renderImage(e, cls, pad, out, w)
            ElementType.VIDEO -> renderMedia("video", e, cls, pad, out, w)
            ElementType.AUDIO -> renderMedia("audio", e, cls, pad, out, w)

            // ---- الروابط والأزرار ----
            ElementType.LINK -> {
                val href = checkedLink(e, w)
                if (href == null) {
                    out.append("$pad<span class=\"$cls\">${inline(text)}</span>\n")
                } else {
                    out.append("$pad<a class=\"$cls\" href=\"${HtmlEscape.attr(href)}\"${relFor(href)}>${inline(text)}</a>\n")
                }
            }
            ElementType.BUTTON -> {
                val href = checkedLink(e, w)
                if (href == null) {
                    // لا يوجد إجراء: نعرض الزر معطّلًا كي لا يوحي بأنه يعمل.
                    if (e.props["href"].isNullOrBlank()) w.add(GenerationWarning(e.id, WarningCode.BUTTON_WITHOUT_ACTION))
                    out.append("$pad<button type=\"button\" class=\"$cls btn\" disabled>${inline(text)}</button>\n")
                } else {
                    out.append("$pad<a class=\"$cls btn\" href=\"${HtmlEscape.attr(href)}\"${relFor(href)}>${inline(text)}</a>\n")
                }
            }
            ElementType.DOWNLOAD_BUTTON -> {
                val href = checkedLink(e, w)
                if (href == null) {
                    if (e.props["href"].isNullOrBlank()) w.add(GenerationWarning(e.id, WarningCode.BUTTON_WITHOUT_ACTION))
                    out.append("$pad<button type=\"button\" class=\"$cls btn\" disabled>${inline(text)}</button>\n")
                } else {
                    out.append("$pad<a class=\"$cls btn\" href=\"${HtmlEscape.attr(href)}\" download${relFor(href)}>${inline(text)}</a>\n")
                }
            }
            ElementType.CALL_BUTTON -> {
                val number = PhoneSanitizer.normalize(e.props["phone"].orEmpty())
                if (number == null) {
                    w.add(GenerationWarning(e.id, WarningCode.INVALID_PHONE, e.props["phone"]))
                    out.append("$pad<button type=\"button\" class=\"$cls btn\" disabled>${inline(text)}</button>\n")
                } else {
                    out.append("$pad<a class=\"$cls btn\" href=\"tel:$number\">${inline(text)}</a>\n")
                }
            }
            ElementType.WHATSAPP_BUTTON -> {
                val number = PhoneSanitizer.whatsappNumber(e.props["phone"].orEmpty())
                if (number == null) {
                    w.add(GenerationWarning(e.id, WarningCode.INVALID_PHONE, e.props["phone"]))
                    out.append("$pad<button type=\"button\" class=\"$cls btn\" disabled>${inline(text)}</button>\n")
                } else {
                    val message = e.props["message"].orEmpty().trim().take(500)
                    val url = "https://wa.me/$number" + if (message.isEmpty()) "" else "?text=" + PercentEncoder.encode(message)
                    out.append("$pad<a class=\"$cls btn\" href=\"${HtmlEscape.attr(url)}\" rel=\"noopener noreferrer\">${inline(text)}</a>\n")
                }
            }
            ElementType.SUBMIT_BUTTON -> {
                if (inForm) {
                    out.append("$pad<button type=\"submit\" class=\"$cls btn\">${inline(text)}</button>\n")
                } else {
                    w.add(GenerationWarning(e.id, WarningCode.SUBMIT_OUTSIDE_FORM))
                    out.append("$pad<button type=\"button\" class=\"$cls btn\" disabled>${inline(text)}</button>\n")
                }
            }

            // ---- النماذج ----
            ElementType.FORM -> renderForm(e, cls, depth, out, w)
            ElementType.TEXT_FIELD -> renderInput("text", e, cls, pad, out, w)
            ElementType.EMAIL_FIELD -> renderInput("email", e, cls, pad, out, w)
            ElementType.NUMBER_FIELD -> renderInput("number", e, cls, pad, out, w)
            ElementType.TEXTAREA_FIELD -> renderTextarea(e, cls, pad, out, w)
            ElementType.CHECKBOX -> renderCheckbox(e, cls, pad, out, w)
            ElementType.SELECT -> renderSelect(e, cls, pad, out, w)
        }
    }

    private fun block(
        tag: String,
        cls: String,
        e: Element,
        depth: Int,
        out: StringBuilder,
        w: MutableList<GenerationWarning>,
        inForm: Boolean,
        extraAttrs: String = "",
    ) {
        val pad = "  ".repeat(depth)
        if (e.children.isEmpty()) {
            out.append("$pad<$tag class=\"$cls\"$extraAttrs></$tag>\n")
            return
        }
        out.append("$pad<$tag class=\"$cls\"$extraAttrs>\n")
        for (child in e.children) render(child, depth + 1, out, w, inForm)
        out.append("$pad</$tag>\n")
    }

    private fun renderList(tag: String, cls: String, e: Element, pad: String, out: StringBuilder) {
        val items = e.props["items"].orEmpty().replace("\r\n", "\n").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (items.isEmpty()) {
            out.append("$pad<$tag class=\"$cls\"></$tag>\n")
            return
        }
        out.append("$pad<$tag class=\"$cls\">\n")
        for (item in items) out.append("$pad  <li>${HtmlEscape.text(item)}</li>\n")
        out.append("$pad</$tag>\n")
    }

    private fun renderImage(e: Element, cls: String, pad: String, out: StringBuilder, w: MutableList<GenerationWarning>) {
        val rawSrc = e.props["src"]
        if (rawSrc.isNullOrBlank()) {
            w.add(GenerationWarning(e.id, WarningCode.MISSING_IMAGE_SOURCE))
            return
        }
        val src = UrlSanitizer.sanitizeImageSource(rawSrc)
        if (src == null) {
            w.add(GenerationWarning(e.id, WarningCode.INVALID_IMAGE_SOURCE, rawSrc))
            return
        }
        val alt = e.props["alt"].orEmpty()
        if (alt.isBlank()) w.add(GenerationWarning(e.id, WarningCode.MISSING_ALT))
        out.append("$pad<img class=\"$cls\" src=\"${HtmlEscape.attr(src)}\" alt=\"${HtmlEscape.attr(alt)}\" loading=\"lazy\">\n")
    }

    private fun renderMedia(tag: String, e: Element, cls: String, pad: String, out: StringBuilder, w: MutableList<GenerationWarning>) {
        val rawSrc = e.props["src"]
        if (rawSrc.isNullOrBlank()) {
            w.add(GenerationWarning(e.id, WarningCode.MISSING_MEDIA_SOURCE))
            return
        }
        val src = UrlSanitizer.sanitizeImageSource(rawSrc)
        if (src == null) {
            w.add(GenerationWarning(e.id, WarningCode.INVALID_MEDIA_SOURCE, rawSrc))
            return
        }
        out.append("$pad<$tag class=\"$cls\" src=\"${HtmlEscape.attr(src)}\" controls preload=\"metadata\"></$tag>\n")
    }

    private fun renderForm(e: Element, cls: String, depth: Int, out: StringBuilder, w: MutableList<GenerationWarning>) {
        val rawAction = e.props["action"].orEmpty().trim()
        var attrs = ""
        if (rawAction.isEmpty()) {
            w.add(GenerationWarning(e.id, WarningCode.FORM_WITHOUT_ACTION))
        } else {
            val ok = UrlSanitizer.sanitizeLink(rawAction)?.takeIf { it.startsWith("https://", ignoreCase = true) }
            if (ok == null) {
                w.add(GenerationWarning(e.id, WarningCode.INVALID_FORM_ACTION, rawAction))
            } else {
                attrs += " action=\"${HtmlEscape.attr(ok)}\""
            }
        }
        val method = if (e.props["method"]?.trim()?.lowercase() == "get") "get" else "post"
        attrs += " method=\"$method\""
        block("form", "$cls form", e, depth, out, w, inForm = true, extraAttrs = attrs)
    }

    /** وسم name صالح أو سلسلة فارغة (مع تحذير إن كان مفقودًا أو غير صالح). */
    private fun nameAttr(e: Element, w: MutableList<GenerationWarning>): String {
        val name = e.props["name"].orEmpty().trim()
        if (!FIELD_NAME.matches(name)) {
            w.add(GenerationWarning(e.id, WarningCode.INVALID_FIELD_NAME, name.ifEmpty { null }))
            return ""
        }
        return " name=\"$name\""
    }

    private fun requiredAttr(e: Element): String = if (e.props["required"]?.trim() == "true") " required" else ""

    private fun labelSpan(e: Element): String {
        val label = e.props["label"].orEmpty()
        return if (label.isBlank()) "" else "<span>${HtmlEscape.text(label)}</span>"
    }

    private fun placeholderAttr(e: Element): String {
        val p = e.props["placeholder"].orEmpty()
        return if (p.isBlank()) "" else " placeholder=\"${HtmlEscape.attr(p)}\""
    }

    private fun renderInput(type: String, e: Element, cls: String, pad: String, out: StringBuilder, w: MutableList<GenerationWarning>) {
        out.append("$pad<label class=\"$cls field\">${labelSpan(e)}<input type=\"$type\"${nameAttr(e, w)}${placeholderAttr(e)}${requiredAttr(e)}></label>\n")
    }

    private fun renderTextarea(e: Element, cls: String, pad: String, out: StringBuilder, w: MutableList<GenerationWarning>) {
        out.append("$pad<label class=\"$cls field\">${labelSpan(e)}<textarea rows=\"4\"${nameAttr(e, w)}${placeholderAttr(e)}${requiredAttr(e)}></textarea></label>\n")
    }

    private fun renderCheckbox(e: Element, cls: String, pad: String, out: StringBuilder, w: MutableList<GenerationWarning>) {
        out.append("$pad<label class=\"$cls field check\"><input type=\"checkbox\"${nameAttr(e, w)} value=\"yes\"${requiredAttr(e)}>${labelSpan(e)}</label>\n")
    }

    private fun renderSelect(e: Element, cls: String, pad: String, out: StringBuilder, w: MutableList<GenerationWarning>) {
        val options = e.props["options"].orEmpty().replace("\r\n", "\n").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val optionsHtml = options.joinToString("") { "<option value=\"${HtmlEscape.attr(it)}\">${HtmlEscape.text(it)}</option>" }
        out.append("$pad<label class=\"$cls field\">${labelSpan(e)}<select${nameAttr(e, w)}${requiredAttr(e)}>$optionsHtml</select></label>\n")
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

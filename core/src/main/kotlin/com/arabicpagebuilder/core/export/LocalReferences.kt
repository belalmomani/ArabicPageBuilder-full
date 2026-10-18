package com.arabicpagebuilder.core.export

import com.arabicpagebuilder.core.generator.UrlSanitizer
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.subtree

enum class RefKind { IMAGE, LINK }

/** إشارة إلى ملف محلي داخل المشروع، بعد إزالة ? و#. */
data class LocalRef(val elementId: String, val kind: RefKind, val path: String)

/** يجمع الإشارات المحلية التي ستظهر فعلًا في HTML الناتج (المقبولة عند المولّد فقط). */
object LocalReferences {

    fun collect(doc: PageDocument): List<LocalRef> {
        val out = ArrayList<LocalRef>()
        for (e in doc.root.subtree()) {
            when (e.type) {
                ElementType.IMAGE -> {
                    val src = e.props["src"] ?: continue
                    val ok = UrlSanitizer.sanitizeImageSource(src) ?: continue
                    UrlSanitizer.sanitizeLocalPath(ok)?.let { out.add(LocalRef(e.id, RefKind.IMAGE, stripQuery(it))) }
                }
                ElementType.LINK, ElementType.BUTTON -> {
                    val href = e.props["href"] ?: continue
                    val ok = UrlSanitizer.sanitizeLink(href) ?: continue
                    if (ok.startsWith("#")) continue
                    UrlSanitizer.sanitizeLocalPath(ok)?.let { out.add(LocalRef(e.id, RefKind.LINK, stripQuery(it))) }
                }
                else -> Unit
            }
        }
        return out
    }

    private fun stripQuery(p: String): String = p.substringBefore('?').substringBefore('#')
}

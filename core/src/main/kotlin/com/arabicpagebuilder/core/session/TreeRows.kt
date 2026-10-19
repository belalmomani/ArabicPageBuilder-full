package com.arabicpagebuilder.core.session

import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.PageDocument

data class TreeRow(val element: Element, val depth: Int)

object TreeRows {
    /** الشجرة مسطّحة بترتيب العرض مع العمق (الجذر أولًا بعمق 0). */
    fun of(doc: PageDocument): List<TreeRow> {
        val out = ArrayList<TreeRow>()
        fun walk(e: Element, depth: Int) {
            out.add(TreeRow(e, depth))
            for (c in e.children) walk(c, depth + 1)
        }
        walk(doc.root, 0)
        return out
    }
}

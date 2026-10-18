package com.arabicpagebuilder.core.generator

/** ترميز آمن لنص المستخدم قبل وضعه في HTML. */
object HtmlEscape {

    /** للنص بين الوسوم. */
    fun text(s: String): String = buildString(s.length + 8) {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(c)
            }
        }
    }

    /** لقيم الخصائص داخل علامات اقتباس مزدوجة. */
    fun attr(s: String): String = buildString(s.length + 8) {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}

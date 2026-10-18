package com.arabicpagebuilder.core.model

/** عقدة في شجرة الصفحة. غير قابلة للتعديل؛ كل تعديل ينتج شجرة جديدة. */
data class Element(
    val id: String,
    val type: ElementType,
    val props: Map<String, String> = emptyMap(),
    val children: List<Element> = emptyList(),
)

/** العنصر وكل أحفاده بترتيب العمق أولًا. */
fun Element.subtree(): Sequence<Element> = sequence {
    yield(this@subtree)
    for (child in children) yieldAll(child.subtree())
}

fun Element.find(id: String): Element? = subtree().firstOrNull { it.id == id }

/** أب العنصر ذي المعرّف المعطى داخل هذه الشجرة، أو null. */
fun Element.findParent(id: String): Element? =
    subtree().firstOrNull { node -> node.children.any { it.id == id } }

package com.arabicpagebuilder.core.edit

import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.IdGenerator
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.find
import com.arabicpagebuilder.core.model.findParent
import com.arabicpagebuilder.core.model.subtree

/**
 * عمليات تعديل الشجرة. كلها دوال نقية: تُرجع مستندًا جديدًا أو سبب الفشل،
 * ولا تغيّر المستند الأصلي أبدًا (فيبقى التراجع بسيطًا وآمنًا).
 */
object DocumentEditor {

    /** يضيف [element] (بكل أبنائه) تحت [parentId] عند [index]، أو في النهاية إن كان null. */
    fun add(doc: PageDocument, parentId: String, element: Element, index: Int? = null): EditResult {
        validateNewSubtree(doc, element)?.let { return fail(it) }
        return insert(doc, parentId, element, index)
    }

    /** يحذف العنصر وكل أحفاده. */
    fun remove(doc: PageDocument, id: String): EditResult {
        if (id == doc.root.id) return fail(EditError.CANNOT_MODIFY_ROOT)
        if (doc.find(id) == null) return fail(EditError.ELEMENT_NOT_FOUND)
        return ok(doc.copy(root = removeNode(doc.root, id)))
    }

    /**
     * ينقل العنصر إلى [newParentId]. [index] هو الموضع داخل قائمة أبناء الوجهة
     * **بعد** إزالة العنصر من مكانه القديم؛ null يعني الإضافة في النهاية.
     */
    fun move(doc: PageDocument, id: String, newParentId: String, index: Int? = null): EditResult {
        if (id == doc.root.id) return fail(EditError.CANNOT_MODIFY_ROOT)
        val element = doc.find(id) ?: return fail(EditError.ELEMENT_NOT_FOUND)
        if (newParentId == id) return fail(EditError.CANNOT_MOVE_INTO_SELF)
        if (element.find(newParentId) != null) return fail(EditError.CANNOT_MOVE_INTO_DESCENDANT)
        if (doc.find(newParentId) == null) return fail(EditError.PARENT_NOT_FOUND)
        val without = doc.copy(root = removeNode(doc.root, id))
        return insert(without, newParentId, element, index)
    }

    /** ينقل العنصر خطوة للأعلى داخل أخوته. */
    fun moveUp(doc: PageDocument, id: String): EditResult = shift(doc, id, -1)

    /** ينقل العنصر خطوة للأسفل داخل أخوته. */
    fun moveDown(doc: PageDocument, id: String): EditResult = shift(doc, id, +1)

    /** ينسخ العنصر (نسخة عميقة بمعرّفات جديدة) ويضعها بعده مباشرة. */
    fun duplicate(doc: PageDocument, id: String, ids: IdGenerator): EditResult {
        if (id == doc.root.id) return fail(EditError.CANNOT_MODIFY_ROOT)
        val element = doc.find(id) ?: return fail(EditError.ELEMENT_NOT_FOUND)
        val parent = doc.root.findParent(id) ?: return fail(EditError.PARENT_NOT_FOUND)
        val position = parent.children.indexOfFirst { it.id == id }
        return insert(doc, parent.id, deepCopy(element, ids), position + 1)
    }

    /** يضبط خاصية للعنصر؛ [value] = null يحذف الخاصية. */
    fun setProperty(doc: PageDocument, id: String, key: String, value: String?): EditResult {
        if (key.isBlank()) return fail(EditError.INVALID_PROPERTY_KEY)
        if (doc.find(id) == null) return fail(EditError.ELEMENT_NOT_FOUND)
        val updated = transform(doc.root, id) { el ->
            val props = el.props.toMutableMap()
            if (value == null) props.remove(key) else props[key] = value
            el.copy(props = props)
        }
        return ok(doc.copy(root = updated))
    }

    // ---- تفاصيل داخلية ----

    private fun ok(doc: PageDocument): EditResult = EditResult.Success(doc)

    private fun fail(error: EditError): EditResult = EditResult.Failure(error)

    private fun validateNewSubtree(doc: PageDocument, element: Element): EditError? {
        val existing = doc.root.subtree().map { it.id }.toSet()
        val seen = HashSet<String>()
        for (node in element.subtree()) {
            if (node.type == ElementType.PAGE_ROOT) return EditError.CANNOT_ADD_ROOT_TYPE
            if (node.id.isBlank() || node.id in existing || !seen.add(node.id)) return EditError.DUPLICATE_ID
            if (node.children.isNotEmpty() && !node.type.acceptsChildren) {
                return EditError.PARENT_CANNOT_HAVE_CHILDREN
            }
        }
        return null
    }

    private fun insert(doc: PageDocument, parentId: String, element: Element, index: Int?): EditResult {
        val parent = doc.find(parentId) ?: return fail(EditError.PARENT_NOT_FOUND)
        if (!parent.type.acceptsChildren) return fail(EditError.PARENT_CANNOT_HAVE_CHILDREN)
        val position = index ?: parent.children.size
        if (position < 0 || position > parent.children.size) return fail(EditError.INDEX_OUT_OF_RANGE)
        val newChildren = parent.children.toMutableList().apply { add(position, element) }
        return ok(doc.copy(root = transform(doc.root, parentId) { it.copy(children = newChildren) }))
    }

    private fun shift(doc: PageDocument, id: String, delta: Int): EditResult {
        if (id == doc.root.id) return fail(EditError.CANNOT_MODIFY_ROOT)
        if (doc.find(id) == null) return fail(EditError.ELEMENT_NOT_FOUND)
        val parent = doc.root.findParent(id) ?: return fail(EditError.PARENT_NOT_FOUND)
        val from = parent.children.indexOfFirst { it.id == id }
        val to = from + delta
        if (to < 0) return fail(EditError.ALREADY_FIRST)
        if (to > parent.children.lastIndex) return fail(EditError.ALREADY_LAST)
        val reordered = parent.children.toMutableList().apply {
            val moved = removeAt(from)
            add(to, moved)
        }
        return ok(doc.copy(root = transform(doc.root, parent.id) { it.copy(children = reordered) }))
    }

    private fun transform(node: Element, id: String, change: (Element) -> Element): Element =
        if (node.id == id) change(node)
        else node.copy(children = node.children.map { transform(it, id, change) })

    private fun removeNode(node: Element, id: String): Element =
        node.copy(children = node.children.filter { it.id != id }.map { removeNode(it, id) })

    private fun deepCopy(node: Element, ids: IdGenerator): Element =
        node.copy(id = ids.next(), children = node.children.map { deepCopy(it, ids) })
}

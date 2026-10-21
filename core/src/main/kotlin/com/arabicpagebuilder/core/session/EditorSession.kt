package com.arabicpagebuilder.core.session

import com.arabicpagebuilder.core.edit.DocumentEditor
import com.arabicpagebuilder.core.edit.EditError
import com.arabicpagebuilder.core.edit.EditResult
import com.arabicpagebuilder.core.edit.History
import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.IdGenerator
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.UuidIdGenerator
import com.arabicpagebuilder.core.model.find
import com.arabicpagebuilder.core.model.findParent
import com.arabicpagebuilder.core.model.subtree

/**
 * جلسة تحرير لمستند واحد: المستند الحالي وسجل التراجع/الإعادة والعنصر المحدد.
 * كلها منطق صرف بلا اعتماد على أندرويد، وواجهة التطبيق تغلّفها فقط.
 * [revision] يزيد عند كل تغيير فعلي في المستند (للحفظ التلقائي وتحديث المعاينة).
 */
class EditorSession(
    initial: PageDocument,
    private val ids: IdGenerator = UuidIdGenerator,
    historyLimit: Int = 100,
) {
    private val history = History(initial, historyLimit)

    val document: PageDocument get() = history.present
    var selectedId: String? = null
        private set
    var revision: Long = 0
        private set

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo
    val selected: Element? get() = selectedId?.let { document.find(it) }

    /** يحدد عنصرًا (أو يلغي التحديد بـ null). يُرجع false إن لم يوجد العنصر. */
    fun select(id: String?): Boolean {
        if (id != null && document.find(id) == null) return false
        selectedId = id
        return true
    }

    /**
     * يضيف عنصرًا جديدًا: داخل المحدد إن كان يقبل أبناء، وإلا بعده في نفس الأب،
     * وإن لم يوجد تحديد ففي نهاية الصفحة. ثم يحدد العنصر الجديد.
     */
    fun addElement(type: ElementType): EditResult {
        if (type == ElementType.PAGE_ROOT) return EditResult.Failure(EditError.CANNOT_ADD_ROOT_TYPE)
        return insertAtSelection(ElementCatalog.defaultElement(type, ids))
    }

    /** يضيف عنصرًا جاهزًا (شجرة كاملة) بنفس قواعد [addElement]. */
    fun addPreset(key: String): EditResult {
        val element = ElementCatalog.buildPreset(key, ids) ?: return EditResult.Failure(EditError.ELEMENT_NOT_FOUND)
        return insertAtSelection(element)
    }

    /** سحب وإفلات: ينقل العنصر [id] إلى [targetId] في [zone]. */
    fun dragMove(id: String, targetId: String, zone: DropZone): EditResult {
        val plan = DropPlanner.plan(document, id, targetId, zone) ?: return invalidDrop(id, targetId)
        return commit(DocumentEditor.move(document, id, plan.parentId, plan.index), newSelection = id)
    }

    /** سحب وإفلات من المكتبة: يضيف عنصرًا جديدًا من نوع [type] عند [targetId] في [zone]. */
    fun dropNewElement(type: ElementType, targetId: String, zone: DropZone): EditResult {
        if (type == ElementType.PAGE_ROOT) return EditResult.Failure(EditError.CANNOT_ADD_ROOT_TYPE)
        return dropNew(ElementCatalog.defaultElement(type, ids), targetId, zone)
    }

    fun dropPreset(key: String, targetId: String, zone: DropZone): EditResult {
        val element = ElementCatalog.buildPreset(key, ids) ?: return EditResult.Failure(EditError.ELEMENT_NOT_FOUND)
        return dropNew(element, targetId, zone)
    }

    private fun dropNew(element: Element, targetId: String, zone: DropZone): EditResult {
        val plan = DropPlanner.plan(document, null, targetId, zone)
            ?: return EditResult.Failure(if (document.find(targetId) == null) EditError.PARENT_NOT_FOUND else EditError.PARENT_CANNOT_HAVE_CHILDREN)
        return commit(DocumentEditor.add(document, plan.parentId, element, plan.index), newSelection = element.id)
    }

    private fun invalidDrop(id: String, targetId: String): EditResult {
        val doc = document
        val dragged = doc.find(id) ?: return EditResult.Failure(EditError.ELEMENT_NOT_FOUND)
        if (doc.find(targetId) == null) return EditResult.Failure(EditError.PARENT_NOT_FOUND)
        if (dragged.id == doc.root.id) return EditResult.Failure(EditError.CANNOT_MODIFY_ROOT)
        if (id == targetId) return EditResult.Failure(EditError.CANNOT_MOVE_INTO_SELF)
        return EditResult.Failure(EditError.CANNOT_MOVE_INTO_DESCENDANT)
    }

    private fun insertAtSelection(element: Element): EditResult {
        val doc = document
        val sel = selected
        val result = when {
            sel == null -> DocumentEditor.add(doc, doc.root.id, element)
            sel.type.acceptsChildren -> DocumentEditor.add(doc, sel.id, element)
            else -> {
                val parent = doc.root.findParent(sel.id)
                if (parent == null) {
                    EditResult.Failure(EditError.PARENT_NOT_FOUND)
                } else {
                    val index = parent.children.indexOfFirst { it.id == sel.id }
                    DocumentEditor.add(doc, parent.id, element, index + 1)
                }
            }
        }
        return commit(result, newSelection = element.id)
    }

    /** يضبط خاصية للعنصر المحدد؛ [value] = null أو فارغ يحذفها. */
    fun setProperty(key: String, value: String?): EditResult {
        val sel = selectedId ?: return EditResult.Failure(EditError.ELEMENT_NOT_FOUND)
        val v = value?.takeIf { it.isNotBlank() }
        return commit(DocumentEditor.setProperty(document, sel, key, v))
    }

    fun removeSelected(): EditResult {
        val sel = selectedId ?: return EditResult.Failure(EditError.ELEMENT_NOT_FOUND)
        val parentId = document.root.findParent(sel)?.id
        return commit(DocumentEditor.remove(document, sel), newSelection = parentId)
    }

    fun duplicateSelected(): EditResult {
        val sel = selectedId ?: return EditResult.Failure(EditError.ELEMENT_NOT_FOUND)
        var firstId: String? = null
        val recording = IdGenerator { ids.next().also { if (firstId == null) firstId = it } }
        return commit(DocumentEditor.duplicate(document, sel, recording), newSelection = firstId)
    }

    fun moveSelectedUp(): EditResult {
        val sel = selectedId ?: return EditResult.Failure(EditError.ELEMENT_NOT_FOUND)
        return commit(DocumentEditor.moveUp(document, sel))
    }

    fun moveSelectedDown(): EditResult {
        val sel = selectedId ?: return EditResult.Failure(EditError.ELEMENT_NOT_FOUND)
        return commit(DocumentEditor.moveDown(document, sel))
    }

    /** ينقل المحدد إلى حاوية أخرى (في نهايتها، أو عند [index]). */
    fun moveSelectedTo(parentId: String, index: Int? = null): EditResult {
        val sel = selectedId ?: return EditResult.Failure(EditError.ELEMENT_NOT_FOUND)
        return commit(DocumentEditor.move(document, sel, parentId, index))
    }

    /** الحاويات التي يجوز نقل المحدد إليها (ليست نفسه ولا أحد أحفاده ولا أبوه الحالي). */
    fun validMoveTargets(): List<TreeRow> {
        val sel = selected ?: return emptyList()
        if (sel.id == document.root.id) return emptyList()
        val parentId = document.root.findParent(sel.id)?.id
        val blocked = sel.subtree().map { it.id }.toSet()
        return TreeRows.of(document).filter {
            it.element.type.acceptsChildren && it.element.id !in blocked && it.element.id != parentId
        }
    }

    fun undo(): Boolean {
        history.undo() ?: return false
        revision++
        fixSelection()
        return true
    }

    fun redo(): Boolean {
        history.redo() ?: return false
        revision++
        fixSelection()
        return true
    }

    private fun commit(result: EditResult, newSelection: String? = null): EditResult {
        if (result is EditResult.Success) {
            val before = history.present
            history.record(result.document)
            if (history.present != before) revision++
            if (newSelection != null) selectedId = newSelection
            fixSelection()
        }
        return result
    }

    private fun fixSelection() {
        val id = selectedId ?: return
        if (document.find(id) == null) selectedId = null
    }
}

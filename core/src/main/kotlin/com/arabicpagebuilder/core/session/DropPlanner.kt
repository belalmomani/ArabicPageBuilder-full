package com.arabicpagebuilder.core.session

import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.find
import com.arabicpagebuilder.core.model.findParent
import com.arabicpagebuilder.core.model.subtree

/** موضع الإفلات بالنسبة إلى العنصر الهدف: قبله، أو بداخله (في نهايته)، أو بعده. */
enum class DropZone { BEFORE, INSIDE, AFTER }

/** (الأب، الموضع) حيث سيوضع العنصر. الموضع محسوب على قائمة أبناء الأب **بعد** إزالة العنصر المسحوب من مكانه. */
data class DropPlan(val parentId: String, val index: Int)

/** يحوّل «إفلات على عنصر في منطقة» إلى (أب، موضع) صالح، أو null إن كان الإفلات غير مسموح. */
object DropPlanner {

    /**
     * [draggedId] = معرّف عنصر موجود يُنقل، أو null لعنصر جديد يُضاف.
     * الإفلات على عنصر ورقي في المنتصف يُعامل كـ «بعده». الإفلات على الجذر دائمًا بداخله.
     */
    fun plan(doc: PageDocument, draggedId: String?, targetId: String, zone: DropZone): DropPlan? {
        val target = doc.find(targetId) ?: return null
        val rootId = doc.root.id
        val dragged = draggedId?.let { id -> doc.find(id) ?: return null }
        if (dragged != null) {
            if (dragged.id == rootId || dragged.id == targetId) return null
            if (dragged.subtree().any { it.id == targetId }) return null // الهدف أحد أحفاده
        }

        val effective = when {
            targetId == rootId -> DropZone.INSIDE
            zone == DropZone.INSIDE && !target.type.acceptsChildren -> DropZone.AFTER
            else -> zone
        }

        val parentId: String
        var index: Int
        if (effective == DropZone.INSIDE) {
            parentId = target.id
            index = target.children.size
        } else {
            val parent = doc.root.findParent(target.id) ?: return null
            parentId = parent.id
            val at = parent.children.indexOfFirst { it.id == target.id }
            index = if (effective == DropZone.BEFORE) at else at + 1
        }

        // بعد إزالة العنصر المسحوب من نفس الأب تنزاح المواضع التي تليه.
        if (dragged != null) {
            val parent = doc.find(parentId) ?: return null
            val oldIndex = parent.children.indexOfFirst { it.id == dragged.id }
            if (oldIndex in 0 until index) index -= 1
        }
        return DropPlan(parentId, index)
    }
}

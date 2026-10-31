package com.arabicpagebuilder.core.canvas

import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.find
import com.arabicpagebuilder.core.model.findParent
import com.arabicpagebuilder.core.model.subtree
import com.arabicpagebuilder.core.session.DropPlanner
import com.arabicpagebuilder.core.session.DropZone

/** [horizontal] = تخطيط الإخوة أفقي (يرسم المؤشر خطًا رأسيًا على جانب الهدف بدل خط أفقي). */
data class ResolvedDrop(val targetId: String, val zone: DropZone, val horizontal: Boolean = false)

/**
 * يحوّل موضع الإصبع فوق المعاينة إلى (عنصر هدف، منطقة) صالحة لـ [DropPlanner].
 * المحور يُستنتج من تخطيط الإخوة: إن كان الهدف بجانب أخ له (يتشاركان النطاق الرأسي) فالتخطيط أفقي
 * (وفي RTL «قبل» = جهة اليمين)، وإلا رأسي. الحاويات تقبل «بداخلها» في الوسط.
 * يُرجع null إن لم يكن الإفلات هناك مسموحًا (مثل داخل العنصر نفسه أو أحد أحفاده).
 */
object DropResolver {

    fun resolve(doc: PageDocument, boxes: Map<String, Box>, x: Double, y: Double, draggedId: String?, rtl: Boolean): ResolvedDrop? {
        val excluded = draggedId?.let { id -> doc.find(id)?.subtree()?.map { it.id }?.toSet() } ?: emptySet()
        // الإصبع ما زال فوق العنصر المسحوب نفسه (أو أحد أحفاده): لا هدف جديد.
        if (draggedId != null && excluded.any { id -> boxes[id]?.contains(x, y) == true }) return null
        val targetId = HitTester.pick(doc, boxes, x, y, excluded) ?: return null
        val target = doc.find(targetId) ?: return null
        val box = boxes[targetId]

        var horizontal = false
        val zone = if (targetId == doc.root.id || box == null || box.area <= 0.0) {
            DropZone.INSIDE
        } else {
            val parent = doc.root.findParent(targetId)
            val siblings = parent?.children.orEmpty().filter { it.id != targetId && it.id !in excluded }.mapNotNull { boxes[it.id] }
            horizontal = siblings.any { it.overlapsVertically(box) && !it.overlapsHorizontally(box) }
            val container = target.type.acceptsChildren
            if (horizontal) {
                val f = if (rtl) (box.right - x) / box.width else (x - box.left) / box.width
                when {
                    container && f in 0.25..0.75 -> DropZone.INSIDE
                    f < 0.5 -> DropZone.BEFORE
                    else -> DropZone.AFTER
                }
            } else {
                val f = (y - box.top) / box.height
                when {
                    container && f in 0.25..0.75 -> DropZone.INSIDE
                    f < 0.5 -> DropZone.BEFORE
                    else -> DropZone.AFTER
                }
            }
        }
        // لا نُظهر هدفًا لن يقبله المحرر.
        return if (DropPlanner.plan(doc, draggedId, targetId, zone) != null) ResolvedDrop(targetId, zone, horizontal) else null
    }
}

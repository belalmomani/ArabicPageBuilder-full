package com.arabicpagebuilder.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.session.DropZone

sealed interface DragPayload {
    data class Existing(val id: String) : DragPayload
    data class NewType(val type: ElementType) : DragPayload
    data class NewPreset(val key: String) : DragPayload
}

data class DropTarget(val id: String, val zone: DropZone)

/**
 * حالة السحب والإفلات. كل الإحداثيات بإحداثيات النافذة. حدود الصفوف تُحدَّث أثناء التخطيط
 * (ليست حالة Compose) فلا تُسبب إعادة تركيب؛ أما الهدف الحالي والمؤشر فحالة تُرسم منها العلامات.
 */
class DragController {
    var payload: DragPayload? by mutableStateOf(null)
        private set
    var label: String by mutableStateOf("")
        private set
    var pointer: Offset by mutableStateOf(Offset.Zero)
        private set
    var target: DropTarget? by mutableStateOf(null)
        private set

    val active: Boolean get() = payload != null

    var listBounds: Rect = Rect.Zero
    var listOrigin: Offset = Offset.Zero
    private val rowBounds = HashMap<String, Rect>()

    fun setRowBounds(id: String, bounds: Rect) {
        rowBounds[id] = bounds
    }

    fun removeRow(id: String) {
        rowBounds.remove(id)
    }

    fun rowAt(p: Offset): String? = rowBounds.entries.firstOrNull { it.value.contains(p) }?.key

    fun start(payload: DragPayload, label: String, p: Offset) {
        this.payload = payload
        this.label = label
        move(p)
    }

    fun move(p: Offset) {
        pointer = p
        recompute()
    }

    /** يُستدعى أيضًا بعد التمرير التلقائي لأن الصفوف تتحرك تحت المؤشر. */
    fun recompute() {
        val current = payload
        if (current == null || !listBounds.contains(pointer)) {
            target = null
            return
        }
        val hit = rowBounds.entries.firstOrNull { it.value.contains(pointer) }
        if (hit == null || (current is DragPayload.Existing && current.id == hit.key)) {
            target = null
            return
        }
        val r = hit.value
        val fraction = (pointer.y - r.top) / r.height
        val zone = when {
            fraction < 0.28f -> DropZone.BEFORE
            fraction > 0.72f -> DropZone.AFTER
            else -> DropZone.INSIDE
        }
        target = DropTarget(hit.key, zone)
    }

    /** ينهي السحب ويُرجع (المحتوى، الهدف) إن كان هناك هدف صالح. */
    fun finish(): Pair<DragPayload, DropTarget>? {
        val p = payload
        val t = target
        cancel()
        return if (p != null && t != null) p to t else null
    }

    fun cancel() {
        payload = null
        target = null
        label = ""
    }
}

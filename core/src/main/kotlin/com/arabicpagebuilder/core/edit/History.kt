package com.arabicpagebuilder.core.edit

/**
 * سجل التراجع والإعادة للقيم غير القابلة للتعديل (لقطات كاملة).
 * [limit] أقصى عدد من خطوات التراجع المحفوظة، لتفادي نمو الذاكرة بلا حد.
 */
class History<T>(initial: T, private val limit: Int = 100) {
    init {
        require(limit > 0) { "limit يجب أن يكون موجبًا" }
    }

    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()

    var present: T = initial
        private set

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /** يسجّل حالة جديدة. القيمة المطابقة للحالية تُتجاهل. أي تسجيل يمسح قائمة الإعادة. */
    fun record(next: T) {
        if (next == present) return
        past.addLast(present)
        if (past.size > limit) past.removeFirst()
        present = next
        future.clear()
    }

    /** يرجع خطوة؛ يُرجع الحالة الجديدة أو null إن لم يكن هناك ما يُتراجع عنه. */
    fun undo(): T? {
        if (past.isEmpty()) return null
        future.addLast(present)
        present = past.removeLast()
        return present
    }

    /** يعيد خطوة؛ يُرجع الحالة الجديدة أو null إن لم يكن هناك ما يُعاد. */
    fun redo(): T? {
        if (future.isEmpty()) return null
        past.addLast(present)
        present = future.removeLast()
        return present
    }
}

package com.arabicpagebuilder.core.canvas

import kotlin.math.max

/** مستطيل بوحدات CSS px (أو أي وحدة موحّدة). */
data class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val centerX: Double get() = (left + right) / 2
    val centerY: Double get() = (top + bottom) / 2
    val area: Double get() = max(0.0, width) * max(0.0, height)

    fun contains(x: Double, y: Double): Boolean = x >= left && x <= right && y >= top && y <= bottom

    /** هل يتقاطعان رأسيًا (يشتركان في نطاق y) بمقدار موجب؟ */
    fun overlapsVertically(o: Box): Boolean = top < o.bottom && o.top < bottom

    /** هل يتقاطعان أفقيًا (يشتركان في نطاق x) بمقدار موجب؟ */
    fun overlapsHorizontally(o: Box): Boolean = left < o.right && o.left < right
}

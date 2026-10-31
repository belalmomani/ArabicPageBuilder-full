package com.arabicpagebuilder.core.canvas

import kotlin.math.roundToInt

/** درجات التكبير والتصغير لمساحة العمل. */
object ZoomMath {
    const val MIN = 0.25
    const val MAX = 3.0
    val steps: List<Double> = listOf(0.25, 0.33, 0.5, 0.67, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0)

    fun clamp(z: Double): Double = if (z.isNaN()) 1.0 else z.coerceIn(MIN, MAX)

    /** الدرجة الأكبر التالية (أو الأقصى). */
    fun zoomIn(z: Double): Double = steps.firstOrNull { it > clamp(z) + 0.001 } ?: MAX

    /** الدرجة الأصغر السابقة (أو الأدنى). */
    fun zoomOut(z: Double): Double = steps.lastOrNull { it < clamp(z) - 0.001 } ?: MIN

    /** تكبير يلائم عرض الصفحة داخل عرض الشاشة (لا يتجاوز 1). */
    fun fit(pageWidth: Double, viewWidth: Double): Double =
        if (pageWidth <= 0.0 || viewWidth <= 0.0) 1.0 else clamp(viewWidth / pageWidth).coerceAtMost(1.0)

    fun label(z: Double): String = "${(clamp(z) * 100).roundToInt()}%"
}

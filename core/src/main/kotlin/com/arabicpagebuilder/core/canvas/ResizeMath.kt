package com.arabicpagebuilder.core.canvas

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/** المقبض المسحوب: العرض (الحافة الطرفية)، أو الارتفاع (الحافة السفلية)، أو الاثنان (الزاوية). */
enum class ResizeHandle { WIDTH, HEIGHT, BOTH }

/** نتيجة تغيير الحجم: قيمة CSS جاهزة للخاصية (مثل "320px" أو "50%") أو null إن لم يتغير ذلك المحور. */
data class ResizeResult(
    val widthCss: String?,
    val heightCss: String?,
    val widthPx: Double?,
    val heightPx: Double?,
    val snappedTo: String?,
)

object ResizeMath {
    const val MIN_SIZE = 8.0
    const val SNAP_DISTANCE = 6.0
    private val percents = listOf(25.0, 100.0 / 3, 50.0, 200.0 / 3, 75.0, 100.0)

    private data class Candidate(val distance: Double, val px: Double, val css: String, val label: String)

    /**
     * يحسب الحجم الجديد من سحب المقبض بمقدار ([dx], [dy]) بوحدات CSS px.
     * في صفحة RTL حافة العرض الطرفية هي اليسرى، فالسحب لليسار (dx سالب) يزيد العرض؛ وفي LTR العكس.
     * يلتصق العرض بنسب الأب الشائعة (25/33/50/67/75/100%) وبعروض الإخوة إن كان على بعد [SNAP_DISTANCE] أو أقل،
     * وإلا يُقرَّب لعدد صحيح من px. الارتفاع بـ px دائمًا.
     */
    fun resize(
        start: Box,
        handle: ResizeHandle,
        dx: Double,
        dy: Double,
        rtl: Boolean,
        parentWidth: Double? = null,
        siblingWidths: List<Double> = emptyList(),
    ): ResizeResult {
        var widthCss: String? = null
        var heightCss: String? = null
        var widthPx: Double? = null
        var heightPx: Double? = null
        var snapped: String? = null

        if (handle == ResizeHandle.WIDTH || handle == ResizeHandle.BOTH) {
            val raw = max(MIN_SIZE, start.width + (if (rtl) -dx else dx))
            val candidates = ArrayList<Candidate>()
            if (parentWidth != null && parentWidth > 0) {
                for (p in percents) {
                    val px = parentWidth * p / 100
                    candidates.add(Candidate(abs(px - raw), px, formatPercent(p), "${formatPercent(p)} من عرض الأب"))
                }
            }
            for (w in siblingWidths) {
                candidates.add(Candidate(abs(w - raw), w, "${formatPx(w)}px", "عرض أخ ${formatPx(w)}px"))
            }
            val best = candidates.filter { it.distance <= SNAP_DISTANCE }.minByOrNull { it.distance }
            if (best != null) {
                widthCss = best.css
                widthPx = round(best.px)
                snapped = best.label
            } else {
                val px = round(raw)
                widthPx = px
                widthCss = "${formatPx(px)}px"
            }
        }
        if (handle == ResizeHandle.HEIGHT || handle == ResizeHandle.BOTH) {
            val h = round(max(MIN_SIZE, start.height + dy))
            heightPx = h
            heightCss = "${formatPx(h)}px"
        }
        return ResizeResult(widthCss, heightCss, widthPx, heightPx, snapped)
    }

    private fun formatPx(v: Double): String = round(v).toLong().toString()

    private fun formatPercent(p: Double): String {
        val r = round(p * 100) / 100
        return (if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()) + "%"
    }
}

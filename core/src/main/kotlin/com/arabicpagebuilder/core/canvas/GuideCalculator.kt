package com.arabicpagebuilder.core.canvas

import kotlin.math.abs
import kotlin.math.round

enum class GuideKind { EDGE, CENTER }

enum class GapSide { LEFT, RIGHT, TOP, BOTTOM }

/**
 * خط محاذاة. [vertical] = خط رأسي عند x = [position] يمتد من [from] إلى [to] (قيم y)،
 * وإلا أفقي عند y = [position] ويمتد من [from] إلى [to] (قيم x).
 */
data class AlignGuide(val vertical: Boolean, val position: Double, val from: Double, val to: Double, val kind: GuideKind, val otherId: String)

/** مسافة بين العنصر المحدد وأقرب جار (أو حافة الأب) في اتجاه، مع طرفي القطعة لرسمها. */
data class GapGuide(val side: GapSide, val distance: Double, val x1: Double, val y1: Double, val x2: Double, val y2: Double, val otherId: String?)

data class Guides(val align: List<AlignGuide>, val gaps: List<GapGuide>)

/** أدلة المحاذاة والمسافات حول العنصر المحدد، بالنسبة لإخوته وأبيه فقط (حتى لا تزدحم الشاشة). */
object GuideCalculator {
    const val MAX_ALIGN_GUIDES = 6

    fun compute(selected: Box, siblings: Map<String, Box>, parent: Box?, tolerance: Double = 1.5): Guides {
        val align = ArrayList<AlignGuide>()
        val seen = HashSet<String>()
        // (القيمة، هل هي المركز؟)
        val selX = listOf(selected.left to false, selected.right to false, selected.centerX to true)
        val selY = listOf(selected.top to false, selected.bottom to false, selected.centerY to true)

        val candidates = siblings.entries.sortedBy { it.key }.map { it.key to it.value } + listOfNotNull(parent?.let { "" to it })
        for ((id, o) in candidates) {
            val oX = listOf(o.left to false, o.right to false, o.centerX to true)
            val oY = listOf(o.top to false, o.bottom to false, o.centerY to true)
            for ((sv, sc) in selX) for ((ov, oc) in oX) {
                if (abs(sv - ov) <= tolerance && seen.add("v:${round(ov * 2)}")) {
                    align.add(AlignGuide(true, ov, minOf(selected.top, o.top), maxOf(selected.bottom, o.bottom), if (sc || oc) GuideKind.CENTER else GuideKind.EDGE, id))
                }
            }
            for ((sv, sc) in selY) for ((ov, oc) in oY) {
                if (abs(sv - ov) <= tolerance && seen.add("h:${round(ov * 2)}")) {
                    align.add(AlignGuide(false, ov, minOf(selected.left, o.left), maxOf(selected.right, o.right), if (sc || oc) GuideKind.CENTER else GuideKind.EDGE, id))
                }
            }
        }
        return Guides(align.take(MAX_ALIGN_GUIDES), gaps(selected, siblings, parent))
    }

    /** أقرب جار في كل اتجاه (يشترك معه في النطاق العمودي على الاتجاه)، وإلا حافة الأب الداخلية. */
    fun gaps(selected: Box, siblings: Map<String, Box>, parent: Box?): List<GapGuide> {
        val out = ArrayList<GapGuide>()
        val others = siblings.entries.sortedBy { it.key }

        // يسار: أقرب جار تقع حافته اليمنى على يسار الحافة اليسرى للمحدد
        val left = others.filter { it.value.overlapsVertically(selected) && it.value.right <= selected.left }.maxByOrNull { it.value.right }
        if (left != null) out.add(GapGuide(GapSide.LEFT, round1(selected.left - left.value.right), left.value.right, selected.centerY, selected.left, selected.centerY, left.key))
        else if (parent != null && selected.left > parent.left) out.add(GapGuide(GapSide.LEFT, round1(selected.left - parent.left), parent.left, selected.centerY, selected.left, selected.centerY, null))

        val right = others.filter { it.value.overlapsVertically(selected) && it.value.left >= selected.right }.minByOrNull { it.value.left }
        if (right != null) out.add(GapGuide(GapSide.RIGHT, round1(right.value.left - selected.right), selected.right, selected.centerY, right.value.left, selected.centerY, right.key))
        else if (parent != null && parent.right > selected.right) out.add(GapGuide(GapSide.RIGHT, round1(parent.right - selected.right), selected.right, selected.centerY, parent.right, selected.centerY, null))

        val top = others.filter { it.value.overlapsHorizontally(selected) && it.value.bottom <= selected.top }.maxByOrNull { it.value.bottom }
        if (top != null) out.add(GapGuide(GapSide.TOP, round1(selected.top - top.value.bottom), selected.centerX, top.value.bottom, selected.centerX, selected.top, top.key))
        else if (parent != null && selected.top > parent.top) out.add(GapGuide(GapSide.TOP, round1(selected.top - parent.top), selected.centerX, parent.top, selected.centerX, selected.top, null))

        val bottom = others.filter { it.value.overlapsHorizontally(selected) && it.value.top >= selected.bottom }.minByOrNull { it.value.top }
        if (bottom != null) out.add(GapGuide(GapSide.BOTTOM, round1(bottom.value.top - selected.bottom), selected.centerX, selected.bottom, selected.centerX, bottom.value.top, bottom.key))
        else if (parent != null && parent.bottom > selected.bottom) out.add(GapGuide(GapSide.BOTTOM, round1(parent.bottom - selected.bottom), selected.centerX, selected.bottom, selected.centerX, parent.bottom, null))

        return out.filter { it.distance >= 0.5 }
    }

    private fun round1(v: Double): Double = round(v * 10) / 10
}

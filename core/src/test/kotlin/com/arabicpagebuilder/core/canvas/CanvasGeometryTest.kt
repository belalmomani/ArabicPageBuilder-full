package com.arabicpagebuilder.core.canvas

import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.TextDirection
import com.arabicpagebuilder.core.session.DropZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanvasGeometryTest {

    private fun el(id: String, type: ElementType, vararg children: Element) = Element(id, type, emptyMap(), children.toList())

    /** r: [a(section): [a1, a2], b, row: [c1, c2]] */
    private fun doc() = PageDocument(
        "ع", TextDirection.RTL,
        el(
            "r", ElementType.PAGE_ROOT,
            el("a", ElementType.SECTION, el("a1", ElementType.PARAGRAPH), el("a2", ElementType.PARAGRAPH)),
            el("b", ElementType.PARAGRAPH),
            el("row", ElementType.ROW, el("c1", ElementType.CARD), el("c2", ElementType.CARD)),
        ),
    )

    private val boxes = mapOf(
        "r" to Box(0.0, 0.0, 360.0, 800.0),
        "a" to Box(0.0, 0.0, 360.0, 200.0),
        "a1" to Box(10.0, 10.0, 350.0, 60.0),
        "a2" to Box(10.0, 70.0, 350.0, 120.0),
        "b" to Box(0.0, 210.0, 360.0, 260.0),
        "row" to Box(0.0, 270.0, 360.0, 400.0),
        "c1" to Box(200.0, 280.0, 350.0, 390.0), // RTL: الأول على اليمين
        "c2" to Box(10.0, 280.0, 190.0, 390.0),
    )

    // ---- Box ----

    @Test
    fun boxBasics() {
        val b = Box(10.0, 20.0, 110.0, 70.0)
        assertEquals(100.0, b.width); assertEquals(50.0, b.height)
        assertEquals(60.0, b.centerX); assertEquals(45.0, b.centerY); assertEquals(5000.0, b.area)
        assertTrue(b.contains(10.0, 20.0) && b.contains(110.0, 70.0) && !b.contains(111.0, 50.0))
        assertTrue(b.overlapsVertically(Box(0.0, 60.0, 5.0, 90.0)))
        assertFalse(b.overlapsVertically(Box(0.0, 70.0, 5.0, 90.0))) // تماس فقط
        assertFalse(b.overlapsHorizontally(Box(110.0, 0.0, 200.0, 5.0)))
    }

    // ---- HitTester ----

    @Test
    fun picksDeepestElementUnderThePoint() {
        assertEquals("a1", HitTester.pick(doc(), boxes, 100.0, 30.0))
        assertEquals("a", HitTester.pick(doc(), boxes, 200.0, 150.0))     // داخل a وخارج أبنائه
        assertEquals("b", HitTester.pick(doc(), boxes, 5.0, 220.0))
        assertEquals("c1", HitTester.pick(doc(), boxes, 300.0, 300.0))
        assertEquals("r", HitTester.pick(doc(), boxes, 180.0, 600.0))     // فراغ الصفحة
    }

    @Test
    fun emptyAreaOutsideEverythingSelectsThePage() {
        assertEquals("r", HitTester.pick(doc(), boxes, 180.0, 5000.0))
        assertEquals("r", HitTester.pick(doc(), emptyMap(), 1.0, 1.0))
    }

    @Test
    fun excludedIdsAndZeroAreaBoxesAreIgnored() {
        assertEquals("a", HitTester.pick(doc(), boxes, 100.0, 30.0, exclude = setOf("a1")))
        val withZero = boxes + ("b" to Box(0.0, 210.0, 0.0, 210.0))
        assertEquals("r", HitTester.pick(doc(), withZero, 0.0, 210.0))
    }

    @Test
    fun unknownIdsInBoxesAreIgnoredAndTiesPreferSmallerArea() {
        val extra = boxes + ("ghost" to Box(0.0, 0.0, 360.0, 800.0))
        assertEquals("a1", HitTester.pick(doc(), extra, 100.0, 30.0))
        val overlapping = mapOf("a1" to Box(0.0, 0.0, 100.0, 100.0), "a2" to Box(0.0, 0.0, 50.0, 50.0), "r" to Box(0.0, 0.0, 360.0, 800.0))
        assertEquals("a2", HitTester.pick(doc(), overlapping, 10.0, 10.0)) // نفس العمق: الأصغر مساحة
    }

    // ---- GuideCalculator ----

    private val sel = Box(100.0, 100.0, 200.0, 150.0)

    @Test
    fun alignsEdgesWithinTolerance() {
        val g = GuideCalculator.compute(sel, mapOf("x" to Box(101.0, 200.0, 180.0, 240.0)), null)
        val v = g.align.single { it.vertical }
        assertEquals(101.0, v.position); assertEquals("x", v.otherId); assertEquals(GuideKind.EDGE, v.kind)
        assertEquals(100.0, v.from); assertEquals(240.0, v.to)
        assertTrue(GuideCalculator.compute(sel, mapOf("x" to Box(103.0, 200.0, 180.0, 240.0)), null).align.none { it.vertical })
    }

    @Test
    fun detectsCenterAlignmentAndHorizontalGuides() {
        val g = GuideCalculator.compute(sel, mapOf("c" to Box(120.0, 300.0, 180.0, 340.0)), null)
        assertTrue(g.align.any { it.vertical && it.position == 150.0 && it.kind == GuideKind.CENTER })
        val h = GuideCalculator.compute(sel, mapOf("y" to Box(300.0, 100.0, 400.0, 150.0)), null)
        assertEquals(setOf(100.0, 125.0, 150.0), h.align.filter { !it.vertical }.map { it.position }.toSet())
    }

    @Test
    fun duplicateGuidePositionsAreMergedAndCapped() {
        val twins = mapOf("p" to Box(100.0, 300.0, 150.0, 340.0), "q" to Box(100.0, 400.0, 160.0, 440.0))
        assertEquals(1, GuideCalculator.compute(sel, twins, null).align.count { it.vertical && it.position == 100.0 })
        val many = (0 until 20).associate { "s$it" to Box(100.0 + it * 0.05, it * 30.0, 100.0 + it * 0.05 + 10 + it, it * 30.0 + 10) }
        assertTrue(GuideCalculator.compute(sel, many, null).align.size <= GuideCalculator.MAX_ALIGN_GUIDES)
    }

    @Test
    fun measuresGapToNearestNeighbourInEachDirection() {
        val sib = mapOf(
            "l" to Box(10.0, 100.0, 60.0, 150.0), "farL" to Box(0.0, 100.0, 20.0, 150.0),
            "r" to Box(260.0, 110.0, 300.0, 140.0),
            "t" to Box(100.0, 20.0, 200.0, 60.0),
            "b" to Box(120.0, 190.0, 180.0, 230.0),
            "diagonal" to Box(10.0, 300.0, 60.0, 350.0),
        )
        val gaps = GuideCalculator.gaps(sel, sib, null).associateBy { it.side }
        assertEquals(40.0, gaps.getValue(GapSide.LEFT).distance); assertEquals("l", gaps.getValue(GapSide.LEFT).otherId)
        assertEquals(60.0, gaps.getValue(GapSide.RIGHT).distance)
        assertEquals(40.0, gaps.getValue(GapSide.TOP).distance)
        assertEquals(40.0, gaps.getValue(GapSide.BOTTOM).distance)
    }

    @Test
    fun fallsBackToParentEdgesAndSkipsTouchingNeighbours() {
        val parent = Box(0.0, 0.0, 360.0, 800.0)
        val gaps = GuideCalculator.gaps(sel, emptyMap(), parent).associateBy { it.side }
        assertEquals(100.0, gaps.getValue(GapSide.LEFT).distance); assertNull(gaps.getValue(GapSide.LEFT).otherId)
        assertEquals(160.0, gaps.getValue(GapSide.RIGHT).distance)
        assertEquals(100.0, gaps.getValue(GapSide.TOP).distance)
        assertEquals(650.0, gaps.getValue(GapSide.BOTTOM).distance)
        val touching = mapOf("t" to Box(0.0, 50.0, 400.0, 100.0)) // تلامس: مسافة صفر
        assertTrue(GuideCalculator.gaps(sel, touching, null).none { it.side == GapSide.TOP })
    }

    // ---- ResizeMath ----

    private val start = Box(0.0, 0.0, 200.0, 100.0)

    @Test
    fun widthGrowsTowardsTheEndEdgeDependingOnDirection() {
        assertEquals("250px", ResizeMath.resize(start, ResizeHandle.WIDTH, -50.0, 0.0, rtl = true).widthCss)
        assertEquals("250px", ResizeMath.resize(start, ResizeHandle.WIDTH, 50.0, 0.0, rtl = false).widthCss)
        assertEquals("150px", ResizeMath.resize(start, ResizeHandle.WIDTH, 50.0, 0.0, rtl = true).widthCss)
        assertNull(ResizeMath.resize(start, ResizeHandle.WIDTH, 50.0, 30.0, rtl = false).heightCss)
    }

    @Test
    fun heightAndBothHandlesAndMinimumSize() {
        val h = ResizeMath.resize(start, ResizeHandle.HEIGHT, 99.0, 30.0, rtl = false)
        assertEquals("130px", h.heightCss); assertNull(h.widthCss)
        val both = ResizeMath.resize(start, ResizeHandle.BOTH, 20.0, -20.0, rtl = false)
        assertEquals("220px", both.widthCss); assertEquals("80px", both.heightCss)
        val tiny = ResizeMath.resize(start, ResizeHandle.BOTH, -1000.0, -1000.0, rtl = false)
        assertEquals("8px", tiny.widthCss); assertEquals("8px", tiny.heightCss)
    }

    @Test
    fun widthSnapsToParentPercentagesAndSiblingWidths() {
        val half = ResizeMath.resize(start, ResizeHandle.WIDTH, -22.0, 0.0, rtl = true, parentWidth = 360.0) // خام 222؟ لا: RTL يزيد 22 → 222
        assertEquals("222px", half.widthCss) // بعيد عن كل النسب (>6px)
        val near = ResizeMath.resize(start, ResizeHandle.WIDTH, 2.0, 0.0, rtl = false, parentWidth = 400.0) // خام 202 ← 50% = 200
        assertEquals("50%", near.widthCss); assertEquals(200.0, near.widthPx); assertTrue(near.snappedTo!!.contains("50%"))
        val third = ResizeMath.resize(Box(0.0, 0.0, 100.0, 50.0), ResizeHandle.WIDTH, 2.0, 0.0, rtl = false, parentWidth = 300.0)
        assertEquals("33.33%", third.widthCss)
        val sibling = ResizeMath.resize(Box(0.0, 0.0, 297.0, 50.0), ResizeHandle.WIDTH, 0.0, 0.0, rtl = false, siblingWidths = listOf(300.0))
        assertEquals("300px", sibling.widthCss); assertTrue(sibling.snappedTo!!.contains("أخ"))
        val none = ResizeMath.resize(start, ResizeHandle.WIDTH, 10.0, 0.0, rtl = false, parentWidth = 400.0) // خام 210، أقرب نسبة 200 (10 px بعيدة)
        assertEquals("210px", none.widthCss); assertNull(none.snappedTo)
    }

    // ---- DropResolver ----

    @Test
    fun verticalFlowGivesBeforeAfterInsideByPosition() {
        val d = doc()
        assertEquals(ResolvedDrop("a1", DropZone.BEFORE), DropResolver.resolve(d, boxes, 180.0, 20.0, "b", rtl = true))
        assertEquals(ResolvedDrop("a1", DropZone.AFTER), DropResolver.resolve(d, boxes, 180.0, 50.0, "b", rtl = true))
        assertEquals(ResolvedDrop("a", DropZone.INSIDE), DropResolver.resolve(d, boxes, 180.0, 130.0, "b", rtl = true))
        assertEquals(ResolvedDrop("b", DropZone.BEFORE), DropResolver.resolve(d, boxes, 180.0, 215.0, "a1", rtl = true))
    }

    @Test
    fun horizontalRowUsesTheStartSideOfTheDirection() {
        val d = doc()
        assertEquals(ResolvedDrop("c1", DropZone.INSIDE, true), DropResolver.resolve(d, boxes, 275.0, 330.0, "b", rtl = true))
        assertEquals(ResolvedDrop("c1", DropZone.BEFORE, true), DropResolver.resolve(d, boxes, 340.0, 330.0, "b", rtl = true))
        assertEquals(ResolvedDrop("c1", DropZone.AFTER, true), DropResolver.resolve(d, boxes, 210.0, 330.0, "b", rtl = true))
        assertEquals(ResolvedDrop("c1", DropZone.BEFORE, true), DropResolver.resolve(d, boxes, 210.0, 330.0, "b", rtl = false))
    }

    @Test
    fun emptyAreaDropsIntoThePageAndNewElementsHaveNoDraggedId() {
        val d = doc()
        assertEquals(ResolvedDrop("r", DropZone.INSIDE), DropResolver.resolve(d, boxes, 180.0, 600.0, "b", rtl = true))
        assertEquals(ResolvedDrop("r", DropZone.INSIDE), DropResolver.resolve(d, boxes, 180.0, 9000.0, null, rtl = true))
        assertEquals(ResolvedDrop("b", DropZone.AFTER), DropResolver.resolve(d, boxes, 180.0, 250.0, null, rtl = true))
    }

    @Test
    fun noTargetWhileOverTheDraggedElementOrItsDescendants() {
        val d = doc()
        assertNull(DropResolver.resolve(d, boxes, 100.0, 30.0, "a", rtl = true))   // فوق ابنه a1
        assertNull(DropResolver.resolve(d, boxes, 300.0, 330.0, "row", rtl = true)) // فوق ابنه c1
        assertNull(DropResolver.resolve(d, boxes, 180.0, 235.0, "b", rtl = true))   // فوق نفسه
        assertNotNull(DropResolver.resolve(d, boxes, 180.0, 235.0, "a1", rtl = true))
    }

    // ---- ZoomMath ----

    @Test
    fun zoomStepsClampAndFit() {
        assertEquals(1.25, ZoomMath.zoomIn(1.0)); assertEquals(0.75, ZoomMath.zoomOut(1.0))
        assertEquals(0.5, ZoomMath.zoomIn(0.33)); assertEquals(3.0, ZoomMath.zoomIn(3.0)); assertEquals(0.25, ZoomMath.zoomOut(0.25))
        assertEquals(1.0, ZoomMath.clamp(Double.NaN)); assertEquals(0.25, ZoomMath.clamp(0.01)); assertEquals(3.0, ZoomMath.clamp(99.0))
        assertEquals(0.28125, ZoomMath.fit(1280.0, 360.0)); assertEquals(1.0, ZoomMath.fit(200.0, 360.0)); assertEquals(1.0, ZoomMath.fit(0.0, 360.0))
        assertEquals(0.25, ZoomMath.fit(5000.0, 360.0))
        assertEquals("50%", ZoomMath.label(0.5)); assertEquals("125%", ZoomMath.label(1.25))
    }

    // ---- RectsParser ----

    @Test
    fun parsesRectsAndIgnoresBadEntries() {
        val ok = RectsParser.parse("""{"a":[1,2,3.5,4],"b":[0,0,10,10]}""")
        assertEquals(Box(1.0, 2.0, 3.5, 4.0), ok["a"]); assertEquals(2, ok.size)
        val mixed = RectsParser.parse("""{"a":[1,2,3],"b":"x","c":[5,5,1,9],"d":[0,0,"q",5],"e":[0,0,4,4]}""")
        assertEquals(setOf("e"), mixed.keys) // ناقص، ونص، ويمين<يسار، وعنصر غير رقمي
    }

    @Test
    fun malformedInputGivesEmptyMapWithoutThrowing() {
        for (bad in listOf(null, "", "null", "[]", "not json", "{", "\"str\"", "123")) assertTrue(RectsParser.parse(bad).isEmpty(), bad.toString())
    }
}

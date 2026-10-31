package com.arabicpagebuilder.core.canvas

import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.find
import com.arabicpagebuilder.core.session.TreeRows

/** يختار العنصر الذي يقع تحت نقطة اللمس من مستطيلات المعاينة. */
object HitTester {

    /** كل العناصر التي تحوي النقطة، من الأعمق إلى الأقل عمقًا (والأصغر مساحة عند التساوي). */
    fun candidates(doc: PageDocument, boxes: Map<String, Box>, x: Double, y: Double, exclude: Set<String> = emptySet()): List<String> {
        val depth = TreeRows.of(doc).associate { it.element.id to it.depth }
        return boxes.entries
            .filter { (id, b) -> id !in exclude && id in depth && b.area > 0.0 && b.contains(x, y) }
            .sortedWith(compareByDescending<Map.Entry<String, Box>> { depth.getValue(it.key) }.thenBy { it.value.area }.thenBy { it.key })
            .map { it.key }
    }

    /** أعمق عنصر تحت النقطة؛ وإن لم يوجد شيء فالصفحة (الجذر) كي يمكن تحديدها بلمس الفراغ. */
    fun pick(doc: PageDocument, boxes: Map<String, Box>, x: Double, y: Double, exclude: Set<String> = emptySet()): String? {
        val hit = candidates(doc, boxes, x, y, exclude).firstOrNull()
        return hit ?: doc.root.id.takeIf { doc.find(it) != null && it !in exclude }
    }
}

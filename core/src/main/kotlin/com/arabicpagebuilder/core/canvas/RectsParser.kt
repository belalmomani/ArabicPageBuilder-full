package com.arabicpagebuilder.core.canvas

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/** يقرأ ناتج `__apb.rects()` من صفحة التحرير: كائن «معرّف العنصر ← [يسار، أعلى، يمين، أسفل]» بوحدات CSS px. */
object RectsParser {
    const val MAX_ENTRIES = 5000

    /** نص JSON → مستطيلات. المدخل التالف أو غير المتوقع يُعطي خريطة فارغة (لا استثناء). */
    fun parse(json: String?): Map<String, Box> {
        if (json.isNullOrBlank() || json == "null") return emptyMap()
        return try {
            val obj: JsonObject = Json.parseToJsonElement(json).jsonObject
            val out = LinkedHashMap<String, Box>()
            for ((id, value) in obj) {
                if (out.size >= MAX_ENTRIES) break
                val arr = value as? JsonArray ?: continue
                if (arr.size != 4) continue
                val n = arr.map { (it as? JsonPrimitive)?.doubleOrNull }
                if (n.any { it == null || it.isNaN() || it.isInfinite() }) continue
                val box = Box(n[0]!!, n[1]!!, n[2]!!, n[3]!!)
                if (box.right >= box.left && box.bottom >= box.top) out[id] = box
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

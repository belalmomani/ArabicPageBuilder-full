package com.arabicpagebuilder.core.model

import java.util.UUID

/** مولّد معرّفات العناصر. يُحقن في العمليات ليسهل اختبارها بمعرّفات ثابتة. */
fun interface IdGenerator {
    fun next(): String
}

object UuidIdGenerator : IdGenerator {
    override fun next(): String = UUID.randomUUID().toString()
}

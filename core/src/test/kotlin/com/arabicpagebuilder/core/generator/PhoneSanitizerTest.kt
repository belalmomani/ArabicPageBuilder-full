package com.arabicpagebuilder.core.generator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneSanitizerTest {

    @Test
    fun normalizesCommonFormats() {
        assertEquals("+962790000000", PhoneSanitizer.normalize("+962 79 000-0000"))
        assertEquals("0790000000", PhoneSanitizer.normalize("0790000000"))
        assertEquals("061234567", PhoneSanitizer.normalize("(06) 123 4567"))
        assertEquals("962790000000", PhoneSanitizer.whatsappNumber("+962790000000"))
    }

    @Test
    fun rejectsInvalidNumbers() {
        for (bad in listOf("", "  ", "12", "+1234567890123456", "abc", "079-ABC-0000", "+", "٠٧٩٠٠٠٠٠٠٠", "tel:+96279", "+96 2+79 0000")) {
            assertNull(PhoneSanitizer.normalize(bad), bad)
        }
    }

    @Test
    fun percentEncodingIsUtf8AndEscapesReservedCharacters() {
        assertEquals("%D9%85%D8%B1%D8%AD%D8%A8%D8%A7", PercentEncoder.encode("مرحبا"))
        assertEquals("a%20b%26c%3Dd", PercentEncoder.encode("a b&c=d"))
        assertEquals("AZaz09-._~", PercentEncoder.encode("AZaz09-._~"))
    }
}

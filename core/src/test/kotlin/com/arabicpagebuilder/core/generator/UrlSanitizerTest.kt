package com.arabicpagebuilder.core.generator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UrlSanitizerTest {

    @Test
    fun acceptsSafeLinks() {
        for (ok in listOf("https://a.com", "http://a.com/x?y=1#z", "mailto:a@b.co", "tel:+962790000000", "#top", "page.html", "assets/x.pdf", "a/b.html?x=1")) {
            assertEquals(ok, UrlSanitizer.sanitizeLink(ok), ok)
        }
    }

    @Test
    fun rejectsDangerousOrMalformedLinks() {
        for (bad in listOf("", "   ", "javascript:alert(1)", "JaVaScRiPt:alert(1)", "data:text/html,x", "vbscript:x", "//evil.com", "/abs/path", "../up.html", "a/../../b", "https://", "mailto:", "#", "ht tp://a.com", "file:///x", "C:\\x\\y", "java\nscript:alert(1)")) {
            assertNull(UrlSanitizer.sanitizeLink(bad), bad)
        }
    }

    @Test
    fun imageSourcesAllowWebAndRelativeOnly() {
        assertEquals("assets/images/a.png", UrlSanitizer.sanitizeImageSource("assets/images/a.png"))
        assertEquals("https://x.com/a.png", UrlSanitizer.sanitizeImageSource("https://x.com/a.png"))
        for (bad in listOf("mailto:a@b.c", "data:image/png;base64,AAAA", "file:///sdcard/a.png", "/data/user/0/a.png", "content://media/1", "../a.png")) {
            assertNull(UrlSanitizer.sanitizeImageSource(bad), bad)
        }
    }
}

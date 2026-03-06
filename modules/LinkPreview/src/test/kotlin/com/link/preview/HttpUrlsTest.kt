package com.link.preview

import org.junit.Assert.*
import org.junit.Test

class HttpUrlsTest {
    @Test
    fun `standalone URLs preserve trailing punctuation and brackets`() {
        for (suffix in listOf(".", ",", "!", ";", ":", "?", ")", "]", "%7D")) {
            val url = "https://example.com/search?q=value$suffix"
            assertEquals(url, HttpUrls.firstIn("  $url  "))
        }
    }

    @Test
    fun `embedded URLs discard interleaved prose punctuation and wrapping brackets`() {
        assertEquals("https://example.com/page", HttpUrls.firstIn("Read (https://example.com/page.), please."))
        assertEquals("https://example.com/page", HttpUrls.firstIn("Read ([https://example.com/page]), please."))
        assertEquals(
            "https://example.com/wiki/Link_(Kotlin)",
            HttpUrls.firstIn("Read ([https://example.com/wiki/Link_(Kotlin)])."),
        )
        assertEquals("http://[::1]", HttpUrls.firstIn("Read [http://[::1]]."))
    }

    @Test
    fun `query-only references retain the page path and encoded characters`() {
        val base = "https://example.com/articles/a%2Fb?old=1#old"
        assertEquals("https://example.com/articles/a%2Fb?page=2#new", HttpUrls.resolve(base, "?page=2#new"))
        assertEquals("https://example.com/articles/a%2Fb?", HttpUrls.resolve(base, "?"))
        assertEquals("https://example.com/articles/a%2Fb?old=1#new", HttpUrls.resolve(base, "#new"))
        assertEquals("https://example.com?next=1", HttpUrls.resolve("https://example.com", "?next=1"))
    }

    @Test
    fun `invalid candidates are skipped without relaxing URL validation`() {
        assertEquals("https://example.com/safe", HttpUrls.firstIn("https://user:secret@example.com then https://example.com/safe"))
        for (value in listOf("javascript:alert(1)", "file:///tmp/page", "https://user:secret@example.com", "https://example.com:0", "https://example.com:65536", "https://example.com/%invalid")) {
            assertNull(HttpUrls.absolute(value))
            assertNull(HttpUrls.resolve("https://example.com/page", value))
        }
    }
}

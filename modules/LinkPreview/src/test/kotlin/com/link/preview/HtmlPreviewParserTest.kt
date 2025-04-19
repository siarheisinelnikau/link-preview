package com.link.preview

import java.nio.charset.Charset
import org.junit.Assert.*
import org.junit.Test

class HtmlPreviewParserTest {
    private val url = "https://example.com/articles/page?one=1&two=2"

    private fun parse(html: String, parser: HtmlPreviewParser = HtmlPreviewParser()): Preview =
        parser.parse(PreviewResponse(url, html.toByteArray()), "https://short.example/link")

    @Test
    fun `Open Graph wins over Twitter and HTML regardless of attribute order or case`() {
        val preview = parse("""
            <title>HTML title</title>
            <meta content='Twitter title' name='twitter:title'>
            <meta content='Open &amp; Graph' PROPERTY='OG:TITLE'>
            <meta name='description' content='HTML description'>
            <meta property='og:description' content='A &lt;b&gt;good&lt;/b&gt; description'>
            <meta property='og:site_name' content='Example'>
        """.trimIndent())
        assertEquals("Open & Graph", preview.title)
        assertEquals("A good description", preview.description)
        assertEquals("Example", preview.siteName)
        assertEquals(url, preview.finalUrl)
        assertEquals("https://short.example/link", preview.url)
    }

    @Test
    fun `Twitter metadata fills missing Open Graph fields`() {
        val preview = parse("""
            <meta property='og:title' content=' '>
            <meta name='twitter:title' content='Card title'>
            <meta name='twitter:description' content='Card description'>
            <meta name='twitter:image' content='/card.jpg'>
        """.trimIndent())
        assertEquals("Card title", preview.title)
        assertEquals("Card description", preview.description)
        assertEquals(listOf("https://example.com/card.jpg"), preview.imageUrls)
    }

    @Test
    fun `relative images canonical and icon resolve against final redirected URL`() {
        val preview = parse("""
            <meta property='og:image' content='../cover.jpg?width=800&amp;height=400'>
            <link rel='canonical' href='./canonical?one=1&amp;two=2'>
            <link rel='shortcut icon' href='//cdn.example/icon.png'>
        """.trimIndent())
        assertEquals(listOf("https://example.com/cover.jpg?width=800&height=400"), preview.imageUrls)
        assertEquals("https://example.com/articles/canonical?one=1&two=2", preview.canonicalUrl)
        assertEquals("https://cdn.example/icon.png", preview.faviconUrl)
    }

    @Test
    fun `base element participates in URL resolution`() {
        val preview = parse("""
            <base href='https://cdn.example/assets/'>
            <meta property='og:image' content='cover.jpg'>
            <link rel='canonical' href='page'>
        """.trimIndent())
        assertEquals(listOf("https://cdn.example/assets/cover.jpg"), preview.imageUrls)
        assertEquals("https://cdn.example/assets/page", preview.canonicalUrl)
    }

    @Test
    fun `metadata and HTML images are ordered deduplicated and limited`() {
        val preview = parse("""
            <meta property='og:image' content='/one.jpg'>
            <meta property='og:image' content='/one.jpg'>
            <meta property='og:image' content='/two.jpg'>
            <img src='/three.webp'><img src='/four.png'>
        """.trimIndent(), HtmlPreviewParser(maxImages = 3))
        assertEquals(listOf("https://example.com/one.jpg", "https://example.com/two.jpg", "https://example.com/three.webp"), preview.imageUrls)
        assertTrue(parse("<img src='/one.jpg'>", HtmlPreviewParser(maxImages = 0)).imageUrls.isEmpty())
    }

    @Test
    fun `non HTTP URLs and credential URLs are discarded`() {
        val preview = parse("""
            <meta property='og:image' content='javascript:alert(1)'>
            <meta property='og:image' content='file:///etc/passwd'>
            <meta property='og:image' content='https://user:secret@example.com/image'>
            <img src='data:image/png;base64,abc'><img src='/safe.jpg'>
            <link rel='canonical' href='javascript:alert(1)'>
        """.trimIndent())
        assertEquals(listOf("https://example.com/safe.jpg"), preview.imageUrls)
        assertNull(preview.canonicalUrl)
    }

    @Test
    fun `HTML fallbacks handle malformed markup and exclude scripts from description`() {
        val preview = parse("""
            <title>Plain &amp; simple</title>
            <script>secret()</script><style>hidden</style><nav>Menu</nav>
            <p>First <b>useful</b> paragraph<p>Second paragraph
        """.trimIndent())
        assertEquals("Plain & simple", preview.title)
        assertEquals("First useful paragraph", preview.description)
    }

    @Test
    fun `description is bounded and empty document still has a useful title`() {
        assertEquals("12345", parse("<p>123456789</p>", HtmlPreviewParser(maxDescriptionLength = 5)).description)
        val empty = parse("")
        assertEquals("example.com", empty.title)
        assertEquals("", empty.description)
        assertTrue(empty.imageUrls.isEmpty())
    }

    @Test
    fun `direct images use Content Type instead of filename extension`() {
        val preview = HtmlPreviewParser().parse(PreviewResponse(url, byteArrayOf(), "IMAGE/WEBP"), url)
        assertEquals(listOf(url), preview.imageUrls)
        assertEquals("", preview.title)
    }

    @Test
    fun `unsupported content type is an explicit failure`() {
        assertThrows(PreviewException.UnsupportedContentType::class.java) {
            HtmlPreviewParser().parse(PreviewResponse(url, "{}".toByteArray(), "application/json"), url)
        }
    }

    @Test
    fun `HTTP charset is honored`() {
        val response = PreviewResponse(
            url,
            "<title>Привет</title>".toByteArray(Charset.forName("windows-1251")),
            "text/html; charset=windows-1251",
        )
        assertEquals("Привет", HtmlPreviewParser().parse(response, url).title)
    }

    @Test
    fun `meta charset works when HTTP charset is missing`() {
        val html = "<meta charset='windows-1251'><title>Привет</title>"
        val response = PreviewResponse(url, html.toByteArray(Charset.forName("windows-1251")), null)
        assertEquals("Привет", HtmlPreviewParser().parse(response, url).title)
    }

    @Test
    fun `Open Graph URL is used when canonical link is absent`() {
        assertEquals("https://example.com/canonical", parse("<meta property='og:url' content='/canonical'>").canonicalUrl)
    }

    @Test
    fun `description cleanup preserves header images and already decoded HTML text`() {
        val preview = parse("""
            <title>Flow&lt;T&gt; &amp; StateFlow</title>
            <header><img src='/hero.webp'>Navigation noise</header>
            <p>Use a List&lt;String&gt; &amp; keep its values.</p>
        """.trimIndent())
        assertEquals("Flow<T> & StateFlow", preview.title)
        assertEquals("Use a List<String> & keep its values.", preview.description)
        assertEquals(listOf("https://example.com/hero.webp"), preview.imageUrls)
    }

    @Test
    fun `metadata that becomes empty after cleanup does not hide fallbacks`() {
        val preview = parse("""
            <meta property='og:title' content='&lt;b&gt;&lt;/b&gt;'>
            <meta name='twitter:title' content='Useful fallback'>
        """.trimIndent())
        assertEquals("Useful fallback", preview.title)
    }
}

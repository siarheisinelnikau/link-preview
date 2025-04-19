package com.link.preview

import java.net.URI
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Open Graph → Twitter Cards → HTML fallbacks. Does not execute scripts or perform network I/O. */
class HtmlPreviewParser(
    private val maxImages: Int = 10,
    private val maxDescriptionLength: Int = 500,
) : PreviewParser {
    init {
        require(maxImages >= 0) { "maxImages cannot be negative" }
        require(maxDescriptionLength > 0) { "maxDescriptionLength must be positive" }
    }

    override fun parse(response: PreviewResponse, requestedUrl: String): Preview {
        val finalUrl = HttpUrls.absolute(response.url) ?: throw PreviewException.InvalidUrl()
        val mediaType = response.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        if (mediaType?.startsWith("image/") == true) {
            return Preview(requestedUrl, finalUrl, "", "", listOf(finalUrl).take(maxImages))
        }
        if (!mediaType.isNullOrEmpty() && mediaType !in setOf("text/html", "application/xhtml+xml")) {
            throw PreviewException.UnsupportedContentType(response.contentType.orEmpty())
        }

        val charset = response.contentType?.let { CHARSET.find(it)?.groupValues?.get(1) }
        val document = response.body.inputStream().use { Jsoup.parse(it, charset, finalUrl) }
        val metadata = metadata(document)
        fun clean(value: String): String = Jsoup.parseBodyFragment(value).text().trim()
        fun meta(vararg names: String): String? = names.asSequence()
            .flatMap { metadata[it].orEmpty().asSequence() }
            .map(::clean).firstOrNull { it.isNotBlank() }

        val title = sequenceOf(meta("og:title", "twitter:title", "title"), document.title(), document.selectFirst("h1")?.text())
            .filterNotNull().firstOrNull { it.isNotBlank() } ?: URI(finalUrl).host
        val description = (meta("og:description", "twitter:description", "description")
            ?: readableText(document)).take(maxDescriptionLength)

        // jsoup resolves <base href> and redirect-relative URLs for DOM attributes.
        val base = HttpUrls.absolute(document.baseUri()) ?: finalUrl
        val imageCandidates = sequenceOf("og:image:secure_url", "og:image", "og:image:url", "twitter:image", "twitter:image:src")
            .flatMap { metadata[it].orEmpty().asSequence() }
            .plus(document.select("link[rel=image_src]").asSequence().map { it.attr("href") })
            .plus(document.select("meta[itemprop=image]").asSequence().map { it.attr("content") })
            .plus(document.select("img[src]").asSequence().map { it.attr("src") })
        val images = imageCandidates.mapNotNull { HttpUrls.resolve(base, it) }.distinct().take(maxImages).toList()
        val canonical = document.select("link[href]").asSequence()
            .filter { "canonical" in it.attr("rel").lowercase(Locale.ROOT).split(Regex("\\s+")) }
            .mapNotNull { HttpUrls.resolve(base, it.attr("href")) }.firstOrNull()
            ?: metadata["og:url"].orEmpty().firstNotNullOfOrNull { HttpUrls.resolve(base, it) }
        val favicon = document.select("link[href]").asSequence()
            .filter { link -> link.attr("rel").lowercase(Locale.ROOT).split(Regex("\\s+")).any { it == "icon" || it == "apple-touch-icon" } }
            .mapNotNull { HttpUrls.resolve(base, it.attr("href")) }.firstOrNull()

        return Preview(
            url = requestedUrl,
            finalUrl = finalUrl,
            title = title,
            description = description,
            imageUrls = images,
            canonicalUrl = canonical,
            siteName = meta("og:site_name", "application-name"),
            faviconUrl = favicon,
        )
    }

    private fun readableText(document: Document): String {
        // Remove text noise from a copy so header/hero images remain available for extraction.
        val readable = document.clone()
        readable.select("script, style, noscript, template, svg, nav, header, footer").remove()
        return readable.select("p").firstOrNull { it.hasText() }?.text() ?: readable.body().text()
    }

    private fun metadata(document: Document): Map<String, List<String>> = buildMap<String, MutableList<String>> {
        for (element in document.select("meta[content]")) {
            val key = element.attr("property").ifBlank { element.attr("name") }.lowercase(Locale.ROOT)
            val value = element.attr("content").trim()
            if (key.isNotBlank() && value.isNotBlank()) getOrPut(key) { mutableListOf() }.add(value)
        }
    }

    private companion object {
        val CHARSET = Regex("charset\\s*=\\s*[\"']?([^\\s;\"']+)", RegexOption.IGNORE_CASE)
    }
}

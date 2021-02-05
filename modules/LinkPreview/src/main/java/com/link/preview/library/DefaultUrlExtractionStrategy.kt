package com.link.preview.library

import com.link.preview.LinkPreLoader.extendedTrim

internal class DefaultUrlExtractionStrategy : UrlExtractionStrategy {

    // Don't forget the http:// or https://
    override fun extractUrls(textPassedToTextCrawler: String): List<String> =
        SearchUrls.matches(textPassedToTextCrawler).apply {
            if (size > 0) this[0] = extendedTrim(this[0])
        }
}

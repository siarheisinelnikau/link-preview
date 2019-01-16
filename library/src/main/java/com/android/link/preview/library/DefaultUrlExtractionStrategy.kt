package com.android.link.preview.library

import com.android.link.preview.LinkPreloader.extendedTrim

internal class DefaultUrlExtractionStrategy : UrlExtractionStrategy {

    // Don't forget the http:// or https://
    override fun extractUrls(textPassedToTextCrawler: String): List<String> =
        SearchUrls.matches(textPassedToTextCrawler).apply {
            if (size > 0) this[0] = extendedTrim(this[0])
        }
}

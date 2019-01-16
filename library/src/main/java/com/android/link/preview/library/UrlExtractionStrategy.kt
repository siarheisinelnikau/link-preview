package com.android.link.preview.library

/**
 * Provides the means for extracting URL(s) from text.
 */
interface UrlExtractionStrategy {
    fun extractUrls(textPassedToTextCrawler: String): List<String>
}

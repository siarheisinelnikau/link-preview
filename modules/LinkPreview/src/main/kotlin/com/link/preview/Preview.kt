package com.link.preview

/** Metadata only: images and icons are URLs, never downloaded by the parser. */
data class Preview(
    val url: String,
    val finalUrl: String,
    val title: String,
    val description: String,
    val imageUrls: List<String>,
    val canonicalUrl: String? = null,
    val siteName: String? = null,
    val faviconUrl: String? = null,
)

sealed interface PreviewState {
    data object Loading : PreviewState
    data class Success(val preview: Preview) : PreviewState
    data class Error(val cause: Exception) : PreviewState
}

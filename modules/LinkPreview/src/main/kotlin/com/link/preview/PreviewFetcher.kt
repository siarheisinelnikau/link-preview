package com.link.preview

/**
 * Network boundary. Implementations must bound decoded response bytes by [PreviewRequest.maxBodyBytes],
 * close their resources, preserve the final URL, and cooperate with coroutine cancellation.
 * Redirect, timeout, authentication, and cache policies belong to the transport.
 */
fun interface PreviewFetcher {
    suspend fun fetch(request: PreviewRequest): PreviewResponse
}

data class PreviewRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val maxBodyBytes: Int = DEFAULT_MAX_BODY_BYTES,
) {
    init {
        require(maxBodyBytes > 0) { "maxBodyBytes must be positive" }
    }

    companion object {
        const val DEFAULT_MAX_BODY_BYTES: Int = 2 * 1024 * 1024
    }
}

/** Raw bytes allow the parser to honor HTTP charset, BOM, and HTML meta charset declarations. */
data class PreviewResponse(
    val url: String,
    val body: ByteArray,
    val contentType: String? = "text/html",
    val statusCode: Int = 200,
)

fun interface PreviewParser {
    fun parse(response: PreviewResponse, requestedUrl: String): Preview
}

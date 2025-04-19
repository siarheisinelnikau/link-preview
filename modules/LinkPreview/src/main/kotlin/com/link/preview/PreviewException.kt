package com.link.preview

import java.io.IOException

sealed class PreviewException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class InvalidUrl : PreviewException("An absolute HTTP or HTTPS URL without credentials is required")

    class Http(val statusCode: Int, val url: String) :
        PreviewException("HTTP $statusCode while loading $url")

    class UnsupportedContentType(val contentType: String) :
        PreviewException("Unsupported content type: $contentType")

    class BodyTooLarge(val maxBodyBytes: Int) :
        PreviewException("Response exceeds $maxBodyBytes bytes")

    class Network(cause: IOException) : PreviewException("Unable to load preview", cause)
}

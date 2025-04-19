package com.link.preview

import java.net.URI
import java.net.URISyntaxException

internal object HttpUrls {
    private val candidate = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    fun firstIn(text: String): String? = candidate.findAll(text).firstNotNullOfOrNull { match ->
        var value = match.value.trimEnd('.', ',', '!', ';', ':', '?')
        for ((open, close) in listOf('(' to ')', '[' to ']', '{' to '}')) {
            while (value.endsWith(close) && value.count { it == close } > value.count { it == open }) {
                value = value.dropLast(1)
            }
        }
        absolute(value)
    }

    fun absolute(value: String): String? = try {
        val uri = URI(value.trim())
        uri.takeIf {
            (it.scheme.equals("http", true) || it.scheme.equals("https", true)) &&
                !it.host.isNullOrBlank() && it.rawUserInfo == null &&
                (it.port == -1 || it.port in 1..65535)
        }?.toASCIIString()
    } catch (_: URISyntaxException) {
        null
    }

    fun resolve(base: String, value: String): String? {
        if (value.isBlank()) return null
        return try {
            absolute(URI(base).resolve(value.trim()).toASCIIString())
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

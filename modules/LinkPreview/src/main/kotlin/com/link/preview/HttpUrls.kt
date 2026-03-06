package com.link.preview

import java.net.URI
import java.net.URISyntaxException

internal object HttpUrls {
    private val candidate = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
    private val closingBrackets = mapOf(')' to '(', ']' to '[', '}' to '{')

    fun firstIn(text: String): String? {
        // A standalone URL is explicit: punctuation may be part of its path or query.
        absolute(text)?.let { return it }
        return candidate.findAll(text).firstNotNullOfOrNull { match ->
            val value = match.value
            val unmatched = closingBrackets.mapValues { (close, open) ->
                value.count { it == close } - value.count { it == open }
            }.toMutableMap()
            absolute(value.trimEnd { char ->
                when {
                    char in ".,!;:?" -> true
                    (unmatched[char] ?: 0) > 0 -> {
                        unmatched[char] = unmatched.getValue(char) - 1
                        true
                    }
                    else -> false
                }
            })
        }
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
            val relative = value.trim()
            // URI.resolve drops the last path segment for query-only references.
            // RFC 3986 section 5.2.2 requires retaining the base path in this case.
            val resolved = if (relative.startsWith('?')) {
                base.substringBefore('#').substringBefore('?') + relative
            } else {
                URI(base).resolve(relative).toASCIIString()
            }
            absolute(resolved)
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

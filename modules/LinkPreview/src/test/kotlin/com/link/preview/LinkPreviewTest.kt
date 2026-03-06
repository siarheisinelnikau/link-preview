package com.link.preview

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkPreviewTest {
    private val url = "https://example.com/page?a=1&b=2"
    private val response = PreviewResponse(url, "<title>Example</title>".toByteArray())

    @Test
    fun `load accepts a URL inside text without truncating query parameters`() = runTest {
        val loader = LinkPreview(PreviewFetcher { request ->
            assertEquals(url, request.url)
            response
        }, dispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(url, loader.load("Read <$url>, please.").url)
    }

    @Test
    fun `balanced URL parentheses and IPv6 brackets are preserved`() = runTest {
        val urls = mutableListOf<String>()
        val loader = LinkPreview(PreviewFetcher { request ->
            urls += request.url
            response.copy(url = request.url)
        }, dispatcher = StandardTestDispatcher(testScheduler))
        loader.load("(https://example.com/wiki/Link_(Kotlin)).")
        loader.load("http://[::1]")
        assertEquals(listOf("https://example.com/wiki/Link_(Kotlin)", "http://[::1]"), urls)
    }

    @Test
    fun `standalone URL punctuation reaches the transport unchanged`() = runTest {
        val original = "https://example.com/search?q=Hello!"
        val loader = LinkPreview(PreviewFetcher { request ->
            assertEquals(original, request.url)
            response.copy(url = request.url)
        }, dispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(original, loader.load(original).url)
    }

    @Test
    fun `invalid input fails before fetching`() = runTest {
        val loader = LinkPreview(PreviewFetcher { error("Network must not be used") }, dispatcher = StandardTestDispatcher(testScheduler))
        for (input in listOf("", "example.com", "file:///etc/passwd", "ftp://example.com", "https://user:secret@example.com", "https://example.com:99999")) {
            assertTrue(runCatching { loader.load(input) }.exceptionOrNull() is PreviewException.InvalidUrl)
        }
    }

    @Test
    fun `transport parser headers and byte limit can be injected`() = runTest {
        val headers = mutableMapOf("User-Agent" to "MyApp")
        val expected = Preview(url, url, "Custom", "", emptyList())
        val loader = LinkPreview(
            fetcher = { request ->
                assertEquals(mapOf("User-Agent" to "MyApp"), request.headers)
                assertEquals(128, request.maxBodyBytes)
                response
            },
            parser = { received, requested ->
                assertSame(response, received)
                assertEquals(url, requested)
                expected
            },
            dispatcher = StandardTestDispatcher(testScheduler),
            maxBodyBytes = 128,
            headers = headers,
        )
        headers["User-Agent"] = "Changed"
        assertSame(expected, loader.load(url))
    }

    @Test
    fun `IO failures retain original cause`() = runTest {
        val cause = IOException("Offline")
        val loader = LinkPreview(PreviewFetcher { throw cause }, dispatcher = StandardTestDispatcher(testScheduler))
        val failure = runCatching { loader.load(url) }.exceptionOrNull()
        assertTrue(failure is PreviewException.Network)
        assertSame(cause, failure?.cause)
    }

    @Test
    fun `custom transport HTTP errors and oversized responses are validated`() = runTest {
        var next = response.copy(statusCode = 404)
        val loader = LinkPreview(PreviewFetcher { next }, dispatcher = StandardTestDispatcher(testScheduler), maxBodyBytes = 32)
        assertEquals(404, (runCatching { loader.load(url) }.exceptionOrNull() as PreviewException.Http).statusCode)
        next = response.copy(body = ByteArray(33))
        assertTrue(runCatching { loader.load(url) }.exceptionOrNull() is PreviewException.BodyTooLarge)
        next = response.copy(url = "file:///tmp/page")
        assertTrue(runCatching { loader.load(url) }.exceptionOrNull() is PreviewException.InvalidUrl)
    }

    @Test
    fun `flow is cold and emits Loading followed by Success for each collection`() = runTest {
        var count = 0
        val loader = LinkPreview(PreviewFetcher { count++; response }, dispatcher = StandardTestDispatcher(testScheduler))
        val flow = loader.observe(url)
        assertEquals(0, count)
        repeat(2) {
            val states = flow.toList()
            assertEquals(PreviewState.Loading, states[0])
            assertEquals("Example", (states[1] as PreviewState.Success).preview.title)
            assertEquals(2, states.size)
        }
        assertEquals(2, count)
    }

    @Test
    fun `flow emits a typed Error on failure`() = runTest {
        val loader = LinkPreview(PreviewFetcher { throw IOException("Offline") }, dispatcher = StandardTestDispatcher(testScheduler))
        val states = loader.observe(url).toList()
        assertEquals(PreviewState.Loading, states.first())
        assertTrue((states.last() as PreviewState.Error).cause is PreviewException.Network)
        assertEquals(2, states.size)
    }

    @Test
    fun `cancelling collection cancels fetcher without emitting Error`() = runTest {
        var cancelled = false
        val loader = LinkPreview(PreviewFetcher {
            try { awaitCancellation() } finally { cancelled = true }
        }, dispatcher = StandardTestDispatcher(testScheduler))
        val states = mutableListOf<PreviewState>()
        val job = launch { loader.observe(url).toList(states) }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(cancelled)
        assertEquals(listOf(PreviewState.Loading), states)
    }

    @Test
    fun `CancellationException and fatal errors are never turned into state errors`() = runTest {
        for (failure in listOf(CancellationException("Cancelled"), AssertionError("Fatal"))) {
            val loader = LinkPreview(PreviewFetcher { throw failure }, dispatcher = StandardTestDispatcher(testScheduler))
            val thrown = runCatching { loader.observe(url).toList() }.exceptionOrNull()
            // Coroutine stack-trace recovery may copy exceptions across dispatcher boundaries.
            assertEquals(failure.javaClass, thrown?.javaClass)
            assertEquals(failure.message, thrown?.message)
        }
    }

    @Test
    fun `collector exception propagates without becoming Error`() = runTest {
        val loader = LinkPreview(PreviewFetcher { response }, dispatcher = StandardTestDispatcher(testScheduler))
        val failure = IllegalStateException("UI failure")
        val received = mutableListOf<PreviewState>()
        assertSame(failure, runCatching {
            loader.observe(url).collect { received += it; throw failure }
        }.exceptionOrNull())
        assertEquals(listOf(PreviewState.Loading), received)
    }
}

package com.link.preview.okhttp

import com.link.preview.LinkPreview
import com.link.preview.PreviewException
import com.link.preview.PreviewRequest
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OkHttpPreviewFetcherTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpPreviewFetcher.defaultClient()
    }

    @After
    fun tearDown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        server.close()
    }

    @Test(timeout = 10000)
    fun `uses injected interceptor and request headers`() = runBlocking {
        server.enqueue(MockResponse(body = "<title>Example</title>"))
        client = client.newBuilder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("X-App", "sample").build())
        }.build()
        val response = OkHttpPreviewFetcher(client).fetch(
            PreviewRequest(server.url("/page?a=1&b=2").toString(), mapOf("User-Agent" to "CustomAgent")),
        )
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/page?a=1&b=2", request.url.encodedPath + "?" + request.url.encodedQuery)
        assertEquals("CustomAgent", request.headers["User-Agent"])
        assertEquals("sample", request.headers["X-App"])
        assertEquals("<title>Example</title>", response.body.toString(Charsets.UTF_8))
    }

    @Test(timeout = 10000)
    fun `redirect preserves final URL for resolving relative images`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/articles/page").build())
        server.enqueue(MockResponse(body = "<meta property='og:image' content='cover.jpg'>"))
        val original = server.url("/short").toString()
        val preview = LinkPreview(OkHttpPreviewFetcher(client)).load(original)
        assertEquals(original, preview.url)
        assertEquals(server.url("/articles/page").toString(), preview.finalUrl)
        assertEquals(listOf(server.url("/articles/cover.jpg").toString()), preview.imageUrls)
    }

    @Test(timeout = 10000)
    fun `client redirect policy is respected`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/other").build())
        client = client.newBuilder().followRedirects(false).build()
        val failure = runCatching { OkHttpPreviewFetcher(client).fetch(PreviewRequest(server.url("/").toString())) }.exceptionOrNull()
        assertEquals(302, (failure as PreviewException.Http).statusCode)
        assertEquals(1, server.requestCount)
    }

    @Test(timeout = 10000)
    fun `non-success status is surfaced`() = runBlocking {
        server.enqueue(MockResponse(code = 503))
        val failure = runCatching { OkHttpPreviewFetcher(client).fetch(PreviewRequest(server.url("/").toString())) }.exceptionOrNull()
        assertEquals(503, (failure as PreviewException.Http).statusCode)
    }

    @Test(timeout = 10000)
    fun `declared body size is bounded`() = runBlocking {
        server.enqueue(MockResponse(body = "x".repeat(65)))
        val failure = runCatching { OkHttpPreviewFetcher(client).fetch(PreviewRequest(server.url("/").toString(), maxBodyBytes = 64)) }.exceptionOrNull()
        assertTrue(failure is PreviewException.BodyTooLarge)
    }

    @Test(timeout = 10000)
    fun `chunked body without Content Length is bounded`() = runBlocking {
        server.enqueue(MockResponse.Builder().chunkedBody("x".repeat(65), 7).build())
        val failure = runCatching { OkHttpPreviewFetcher(client).fetch(PreviewRequest(server.url("/").toString(), maxBodyBytes = 64)) }.exceptionOrNull()
        assertTrue(failure is PreviewException.BodyTooLarge)
    }

    @Test(timeout = 10000)
    fun `gzip limit applies to decompressed bytes`() = runBlocking {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write("x".repeat(4096).toByteArray()) }
        server.enqueue(MockResponse.Builder().body(Buffer().write(output.toByteArray())).addHeader("Content-Encoding", "gzip").build())
        val failure = runCatching { OkHttpPreviewFetcher(client).fetch(PreviewRequest(server.url("/").toString(), maxBodyBytes = 128)) }.exceptionOrNull()
        assertTrue(failure is PreviewException.BodyTooLarge)
    }

    @Test(timeout = 10000)
    fun `image responses do not buffer the image body`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("x".repeat(4096)).addHeader("Content-Type", "image/webp").build())
        val url = server.url("/image-without-extension").toString()
        val response = OkHttpPreviewFetcher(client).fetch(PreviewRequest(url, maxBodyBytes = 64))
        assertEquals("image/webp", response.contentType)
        assertTrue(response.body.isEmpty())
    }

    @Test(timeout = 10000)
    fun `cancellation while reading the body cancels the underlying HTTP call`() = runBlocking {
        val reading = CountDownLatch(1)
        var observedCall: Call? = null
        client = client.newBuilder().eventListener(object : EventListener() {
            override fun callStart(call: Call) {
                observedCall = call
            }
        }).addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val originalBody = response.body
            response.newBuilder().body(object : ResponseBody() {
                private val stream = object : ForwardingSource(originalBody.source()) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        reading.countDown()
                        return super.read(sink, byteCount)
                    }
                }.buffer()
                override fun contentType() = originalBody.contentType()
                override fun contentLength() = originalBody.contentLength()
                override fun source() = stream
            }).build()
        }.build()
        server.enqueue(MockResponse.Builder().body("<title>Delayed</title>").bodyDelay(1, TimeUnit.SECONDS).build())
        val pending = async(Dispatchers.Default) { OkHttpPreviewFetcher(client).fetch(PreviewRequest(server.url("/").toString())) }
        assertTrue(withContext(Dispatchers.IO) { reading.await(3, TimeUnit.SECONDS) })
        pending.cancelAndJoin()
        assertTrue(observedCall!!.isCanceled())
    }

    @Test(timeout = 10000)
    fun `client call timeout propagates as a network failure`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("slow").headersDelay(1, TimeUnit.SECONDS).build())
        client = client.newBuilder().callTimeout(100, TimeUnit.MILLISECONDS).build()
        val failure = runCatching { LinkPreview(OkHttpPreviewFetcher(client)).load(server.url("/").toString()) }.exceptionOrNull()
        assertTrue(failure is PreviewException.Network)
    }
}

package com.link.preview.okhttp

import com.link.preview.PreviewException
import com.link.preview.PreviewFetcher
import com.link.preview.PreviewRequest
import com.link.preview.PreviewResponse
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Uses the supplied client's interceptors, cache, cookies, redirects, and timeouts unchanged.
 * Cancellation cancels the Call, including response-body reads. The caller owns the client.
 */
class OkHttpPreviewFetcher(
    private val client: OkHttpClient = defaultClient(),
) : PreviewFetcher {
    override suspend fun fetch(request: PreviewRequest): PreviewResponse = suspendCancellableCoroutine { continuation ->
        val httpRequest = Request.Builder().url(request.url).apply {
            header("User-Agent", "LinkPreview/2.0")
            header("Accept", "text/html, application/xhtml+xml, image/*;q=0.8")
            request.headers.forEach { (name, value) -> header(name, value) }
        }.build()
        val call = client.newCall(httpRequest)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                // Consume and close before resuming: cancellation cannot strand an open Response.
                val result = runCatching {
                    response.use {
                        if (!continuation.isActive) return
                        if (!it.isSuccessful) throw PreviewException.Http(it.code, it.request.url.toString())
                        val body = it.body
                        val type = body.contentType()?.toString()
                        val bytes = if (type?.startsWith("image/", ignoreCase = true) == true) {
                            byteArrayOf()
                        } else {
                            if (body.contentLength() > request.maxBodyBytes) {
                                throw PreviewException.BodyTooLarge(request.maxBodyBytes)
                            }
                            body.byteStream().use { input ->
                                val output = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                var total = 0L
                                while (true) {
                                    if (!continuation.isActive) return
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    total += read
                                    if (total > request.maxBodyBytes) throw PreviewException.BodyTooLarge(request.maxBodyBytes)
                                    output.write(buffer, 0, read)
                                }
                                output.toByteArray()
                            }
                        }
                        PreviewResponse(it.request.url.toString(), bytes, type, it.code)
                    }
                }
                result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }

    companion object {
        /** Bounded defaults; inject your app's shared client to customize network policy. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

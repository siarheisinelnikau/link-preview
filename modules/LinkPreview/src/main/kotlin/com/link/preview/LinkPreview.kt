package com.link.preview

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/** Reusable, main-safe loader. No global scope, Android Context, or implicit HTTP client. */
class LinkPreview(
    private val fetcher: PreviewFetcher,
    private val parser: PreviewParser = HtmlPreviewParser(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxBodyBytes: Int = PreviewRequest.DEFAULT_MAX_BODY_BYTES,
    headers: Map<String, String> = emptyMap(),
) {
    private val headers = headers.toMap()

    init {
        require(maxBodyBytes > 0) { "maxBodyBytes must be positive" }
    }

    /** Loads an explicit HTTP(S) URL unchanged, or the first URL in [text]. Cancellation is rethrown. */
    suspend fun load(text: String): Preview = withContext(dispatcher) {
        val url = HttpUrls.firstIn(text) ?: throw PreviewException.InvalidUrl()
        val response = try {
            fetcher.fetch(PreviewRequest(url, headers, maxBodyBytes))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: PreviewException) {
            throw failure
        } catch (failure: IOException) {
            throw PreviewException.Network(failure)
        }
        currentCoroutineContext().ensureActive()
        if (response.statusCode !in 200..299) throw PreviewException.Http(response.statusCode, response.url)
        if (response.body.size > maxBodyBytes) throw PreviewException.BodyTooLarge(maxBodyBytes)
        if (HttpUrls.absolute(response.url) == null) throw PreviewException.InvalidUrl()
        parser.parse(response, url).also { currentCoroutineContext().ensureActive() }
    }

    /** Cold flow: each collection starts a new load. Collector failures and cancellation propagate. */
    fun observe(text: String): Flow<PreviewState> = flow<PreviewState> {
        emit(PreviewState.Loading)
        emit(PreviewState.Success(load(text)))
    }.catch { failure ->
        if (failure is CancellationException || failure !is Exception) throw failure
        emit(PreviewState.Error(failure))
    }
}

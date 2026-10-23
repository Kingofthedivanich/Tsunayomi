package app.tsunayomi.source.mangadex

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MangaDex requires every image loaded from a MangaDex@Home node (any host outside mangadex.org) to be reported,
 * success or failure, with its size and full download time. Installed on the shared client, so Coil loads count.
 */
class MangaDexAtHomeReporter(
    private val reportUrl: String = REPORT_URL,
    /** Lazy: in the app this is the shared client, which itself contains this interceptor. */
    private val reportClient: () -> OkHttpClient,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isAtHomeImage(request.url)) return chain.proceed(request)

        val start = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            report(request.url, success = false, cached = false, bytes = 0, start)
            throw e
        }
        val cached = response.header("X-Cache")?.startsWith("HIT") == true
        if (!response.isSuccessful) {
            report(request.url, success = false, cached = cached, bytes = 0, start)
            return response
        }

        val body = response.body
        val expected = body.contentLength()
        val reported = AtomicBoolean(false)
        var received = 0L
        fun finish(success: Boolean) {
            if (reported.compareAndSet(false, true)) report(request.url, success, cached, received, start)
        }
        val counting = object : ForwardingSource(body.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = try {
                    super.read(sink, byteCount)
                } catch (e: IOException) {
                    finish(success = false)
                    throw e
                }
                if (read == -1L) finish(success = true) else received += read
                return read
            }

            override fun close() {
                // Closed early (e.g. the request was cancelled) counts as success only if everything arrived.
                finish(success = expected >= 0 && received >= expected)
                super.close()
            }
        }
        return response.newBuilder().body(counting.buffer().asResponseBody(body.contentType(), expected)).build()
    }

    private fun report(url: HttpUrl, success: Boolean, cached: Boolean, bytes: Long, startNanos: Long) {
        val payload = Report(
            url = url.toString(),
            success = success,
            cached = cached,
            bytes = bytes,
            duration = (System.nanoTime() - startNanos) / 1_000_000,
        )
        val request = Request.Builder()
            .url(reportUrl)
            .post(MangaDexSource.json.encodeToString(payload).toRequestBody(JSON))
            .build()
        reportClient().newCall(request).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) = Unit
                override fun onResponse(call: Call, response: Response) = response.close()
            },
        )
    }

    @Serializable
    private data class Report(val url: String, val success: Boolean, val cached: Boolean, val bytes: Long, val duration: Long)

    companion object {
        const val REPORT_URL = "https://api.mangadex.network/report"
        private val JSON = "application/json".toMediaType()
        private val AT_HOME_PATH = Regex("""/(data|data-saver)/[0-9a-f]{32}/[^/]+$""")

        internal fun isAtHomeImage(url: HttpUrl): Boolean =
            !url.host.endsWith("mangadex.org") && AT_HOME_PATH.containsMatchIn(url.encodedPath)
    }
}

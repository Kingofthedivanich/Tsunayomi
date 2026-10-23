package app.tsunayomi.extension.manager

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider

/**
 * Image addresses of extension sources point at a made-up host and carry the source id, so every image loader in
 * the app (reader, covers, downloads, translation) can fetch them with the shared client while the actual request
 * goes through the extension, with its headers and interceptors. The address stays stable, so caches keep working.
 */
internal object ExtensionUrls {
    const val HOST = "extension.tsunayomi.invalid"

    fun image(sourceId: Long, imageUrl: String): String = build("image", sourceId) { addQueryParameter("url", imageUrl) }

    fun page(sourceId: Long, pageUrl: String, index: Int): String = build("page", sourceId) {
        addQueryParameter("url", pageUrl)
        addQueryParameter("index", index.toString())
    }

    fun cover(sourceId: Long, coverUrl: String): String = build("cover", sourceId) { addQueryParameter("url", coverUrl) }

    /** The site's own address behind one of ours, or the input if it isn't ours. */
    fun original(url: String): String {
        val parsed = runCatching { url.toHttpUrl() }.getOrNull()
        return if (parsed?.host == HOST) parsed.queryParameter("url") ?: url else url
    }

    private fun build(kind: String, sourceId: Long, params: HttpUrl.Builder.() -> Unit) = HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment(kind)
        .addPathSegment(sourceId.toString())
        .apply(params)
        .build()
        .toString()
}

internal class ExtensionImageInterceptor @Inject constructor(
    // Lazy: the manager itself needs the shared client this interceptor is part of.
    private val manager: Provider<ExtensionManager>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != ExtensionUrls.HOST) return chain.proceed(request)

        val kind = url.pathSegments.getOrNull(0)
        val sourceId = url.pathSegments.getOrNull(1)?.toLongOrNull() ?: throw IOException("Bad extension URL: $url")
        val target = url.queryParameter("url") ?: throw IOException("Bad extension URL: $url")
        val source = manager.get().catalogSource(sourceId) ?: throw IOException("Extension for source $sourceId is not installed")
        val response = try {
            runBlocking {
                when (kind) {
                    "image" -> source.fetchImage(pageUrl = "", pageIndex = 0, imageUrl = target)
                    "page" -> source.fetchImage(target, url.queryParameter("index")?.toIntOrNull() ?: 0, imageUrl = null)
                    "cover" -> source.fetchCover(target)
                    else -> throw IOException("Bad extension URL: $url")
                }
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Throwable) {
            // OkHttp only expects IOExceptions from interceptors; anything else would crash the calling thread.
            throw IOException(e.message ?: e.toString(), e)
        }
        return response.newBuilder().request(request).build()
    }
}

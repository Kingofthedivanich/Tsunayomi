package app.tsunayomi.source.kotatsu

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import org.koitharu.kotatsu.parsers.model.MangaPage
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
internal class KotatsuImageInterceptor @Inject constructor(
    private val sourceManager: Provider<KotatsuSourceManager>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != KotatsuUrls.HOST) return chain.proceed(request)

        val sourceId = url.pathSegments.getOrNull(1)?.toLongOrNull()
            ?: throw IOException("Bad Kotatsu URL (missing source id): $url")
        val pageUrl = url.queryParameter("url")
            ?: throw IOException("Bad Kotatsu URL (missing url parameter): $url")
        val preview = url.queryParameter("preview")
        val pageId = url.queryParameter("id")?.toLongOrNull() ?: 0L

        val manager = sourceManager.get()
        val catalogSource = manager.getSource(sourceId)
            ?: throw IOException("Kotatsu source $sourceId is not available")

        val parser = catalogSource.parser
        val mangaPage = MangaPage(
            id = pageId,
            url = pageUrl,
            preview = preview,
            source = catalogSource.parserSource,
        )

        val response = try {
            runBlocking {
                val resolvedImageUrl = parser.getPageUrl(mangaPage)
                val imageHeaders = parser.getRequestHeaders().newBuilder().apply {
                    if (get("Referer") == null) {
                        set("Referer", "https://${parser.domain}/")
                    }
                }.build()

                val client = manager.loaderContext.httpClient
                    .newBuilder()
                    .addInterceptor(parser)
                    .build()

                val imageReq = request.newBuilder()
                    .url(resolvedImageUrl)
                    .headers(imageHeaders)
                    .build()

                client.newCall(imageReq).execute()
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Throwable) {
            throw IOException(e.message ?: e.toString(), e)
        }

        return response.newBuilder().request(request).build()
    }
}

package app.tsunayomi.source.remanga

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that injects necessary Referer and User-Agent headers for Remanga API and CDN images,
 * and transparently falls back to DDoS-Guard mirror (img-reserve.reimg2.org) if the primary image
 * host (img.reimg.org) returns a Cloudflare block (403/429).
 */
class RemangaInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        val isRemanga = host.contains("remanga.org") || host.contains("reimg.org") || host.contains("reimg2.org")
        if (!isRemanga) {
            return chain.proceed(request)
        }

        val requestBuilder = request.newBuilder()
        if (request.header("Referer") == null) {
            requestBuilder.header("Referer", REFERER)
        }
        if (request.header("User-Agent") == null) {
            requestBuilder.header("User-Agent", USER_AGENT)
        }

        val newRequest = requestBuilder.build()
        val response = chain.proceed(newRequest)

        // If primary CDN (Cloudflare) blocks the request, fall back to DDoS-Guard CDN mirror
        if ((response.code == 403 || response.code == 429) && host == PRIMARY_IMAGE_HOST) {
            response.close()
            val fallbackUrl = newRequest.url.newBuilder()
                .host(FALLBACK_IMAGE_HOST)
                .build()
            val fallbackRequest = newRequest.newBuilder()
                .url(fallbackUrl)
                .build()
            return chain.proceed(fallbackRequest)
        }

        return response
    }

    companion object {
        const val REFERER = "https://remanga.org/"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        const val PRIMARY_IMAGE_HOST = "img.reimg.org"
        const val FALLBACK_IMAGE_HOST = "img-reserve.reimg2.org"
    }
}

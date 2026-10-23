package app.tsunayomi.source.asura

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that injects necessary Referer and User-Agent headers for Asura Scans API and CDN images.
 */
class AsuraInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        if (!host.contains("asurascans.com")) {
            return chain.proceed(request)
        }

        val requestBuilder = request.newBuilder()
        if (request.header("Referer") == null) {
            requestBuilder.header("Referer", REFERER)
        }
        if (request.header("User-Agent") == null) {
            requestBuilder.header("User-Agent", USER_AGENT)
        }

        return chain.proceed(requestBuilder.build())
    }

    companion object {
        const val REFERER = "https://asurascans.com/"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }
}

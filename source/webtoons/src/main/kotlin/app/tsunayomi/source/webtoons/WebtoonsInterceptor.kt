package app.tsunayomi.source.webtoons

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that attaches the required Referer header when fetching images from Naver / Webtoon CDN (pstatic.net).
 */
class WebtoonsInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        return if (host.contains("pstatic.net") || host.contains("webtoons.com")) {
            val newRequest = request.newBuilder()
                .header("Referer", "https://www.webtoons.com/")
                .build()
            chain.proceed(newRequest)
        } else {
            chain.proceed(request)
        }
    }
}

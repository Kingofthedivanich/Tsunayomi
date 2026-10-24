package app.tsunayomi.source.naver

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that attaches the required Referer header when fetching images from Korean Naver Webtoon (image-comic.pstatic.net).
 */
class NaverInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        return if (host == "image-comic.pstatic.net") {
            val newRequest = request.newBuilder()
                .header("Referer", "https://comic.naver.com/")
                .build()
            chain.proceed(newRequest)
        } else {
            chain.proceed(request)
        }
    }
}

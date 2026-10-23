package app.tsunayomi.core.network

import android.os.Build
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Sets an honest User-Agent unless a source already provided its own.
 * MangaDex forbids spoofed browser agents; sources that need one must set it per request.
 */
internal class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(HEADER) != null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(HEADER, USER_AGENT).build())
    }

    companion object {
        private const val HEADER = "User-Agent"
        val USER_AGENT = "Tsunayomi (Android ${Build.VERSION.RELEASE})"
    }
}

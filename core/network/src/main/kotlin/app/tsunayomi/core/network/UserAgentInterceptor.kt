package app.tsunayomi.core.network

import okhttp3.Interceptor
import okhttp3.Response

/** Sets a default User-Agent unless a source already provided its own. */
internal class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(HEADER) != null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(HEADER, DEFAULT_USER_AGENT).build())
    }

    private companion object {
        const val HEADER = "User-Agent"
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }
}

package app.tsunayomi.core.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Credentials for user-configured servers (Komga, …), keyed by scheme+host+port.
 * Every request through the shared client — API calls and Coil image loads alike — gets them,
 * so covers and pages behind auth load without per-request plumbing.
 */
@Singleton
class HostAuthRegistry @Inject constructor() {
    private val headersByOrigin = ConcurrentHashMap<String, Map<String, String>>()

    fun set(url: HttpUrl, headers: Map<String, String>) {
        headersByOrigin[url.origin()] = headers
    }

    fun clear(url: HttpUrl) {
        headersByOrigin.remove(url.origin())
    }

    internal fun headersFor(url: HttpUrl): Map<String, String>? = headersByOrigin[url.origin()]

    /** For tests in other modules that stand in for the shared client. */
    fun headersForTest(url: HttpUrl): Map<String, String>? = headersFor(url)

    private fun HttpUrl.origin() = "$scheme://$host:$port"
}

internal class HostAuthInterceptor(private val registry: HostAuthRegistry) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val headers = registry.headersFor(request.url) ?: return chain.proceed(request)
        val builder = request.newBuilder()
        headers.forEach { (name, value) -> if (request.header(name) == null) builder.header(name, value) }
        return chain.proceed(builder.build())
    }
}

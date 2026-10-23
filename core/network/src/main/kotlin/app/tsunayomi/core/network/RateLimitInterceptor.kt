package app.tsunayomi.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

/**
 * Allows at most [permits] requests per [period]; extra requests wait their turn.
 * Add it to a per-source client built with `newBuilder()`, never to the shared one.
 */
class RateLimitInterceptor(
    private val permits: Int,
    period: Long,
    unit: TimeUnit = TimeUnit.SECONDS,
) : Interceptor {
    private val periodNanos = unit.toNanos(period)
    private val sent = ArrayDeque<Long>(permits)
    private val lock = Object()

    override fun intercept(chain: Interceptor.Chain): Response {
        synchronized(lock) {
            while (true) {
                val now = System.nanoTime()
                while (sent.isNotEmpty() && now - sent.peekFirst() >= periodNanos) sent.pollFirst()
                if (sent.size < permits) {
                    sent.addLast(now)
                    break
                }
                val waitNanos = periodNanos - (now - sent.peekFirst())
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, waitNanos)
                } catch (e: InterruptedException) {
                    throw IOException("Interrupted while waiting for rate limit", e)
                }
                if (chain.call().isCanceled()) throw IOException("Canceled")
            }
        }
        return chain.proceed(chain.request())
    }
}

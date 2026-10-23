package app.tsunayomi.translation.gemini

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers which models are out of quota and until when, so the chain skips them without wasting a request.
 * In memory only: after a restart an exhausted model costs one rejected request before being skipped again.
 */
internal class ModelQuota(private val now: () -> Long = System::currentTimeMillis) {
    private val blockedUntil = ConcurrentHashMap<String, Long>()

    fun isAvailable(model: String): Boolean = (blockedUntil[model] ?: 0L) <= now()

    fun block(model: String, untilMillis: Long) {
        blockedUntil[model] = untilMillis
    }

    /** Daily free quotas reset at midnight Pacific time. */
    fun nextDailyReset(): Long {
        val zone = ZoneId.of("America/Los_Angeles")
        val today = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
        return LocalDate.from(today).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun inSeconds(seconds: Long): Long = now() + seconds * 1000
}

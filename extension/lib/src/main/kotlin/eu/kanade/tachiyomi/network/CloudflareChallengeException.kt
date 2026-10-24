package eu.kanade.tachiyomi.network

import java.io.IOException

/**
 * Thrown when a request is blocked by Cloudflare anti-bot mitigation and cannot be bypassed automatically.
 */
class CloudflareChallengeException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * Checks whether this throwable or any of its causes was caused by a Cloudflare challenge block.
 */
val Throwable.isCloudflareChallenge: Boolean
    get() {
        var current: Throwable? = this
        while (current != null) {
            if (current is CloudflareChallengeException) return true
            current = current.cause
        }
        return false
    }

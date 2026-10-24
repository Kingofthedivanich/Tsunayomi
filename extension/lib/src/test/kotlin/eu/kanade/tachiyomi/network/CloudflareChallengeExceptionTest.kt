package eu.kanade.tachiyomi.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class CloudflareChallengeExceptionTest {

    @Test
    fun `isCloudflareChallenge returns true for CloudflareChallengeException`() {
        val ex = CloudflareChallengeException("Blocked by Cloudflare")
        assertTrue(ex.isCloudflareChallenge)
    }

    @Test
    fun `isCloudflareChallenge returns true when wrapped in another exception`() {
        val root = CloudflareChallengeException("Blocked by Cloudflare")
        val wrapped = IOException("Network error", RuntimeException("Intermediate", root))
        assertTrue(wrapped.isCloudflareChallenge)
    }

    @Test
    fun `isCloudflareChallenge returns false for normal exceptions`() {
        val normal = IOException("Socket timeout")
        assertFalse(normal.isCloudflareChallenge)
    }
}

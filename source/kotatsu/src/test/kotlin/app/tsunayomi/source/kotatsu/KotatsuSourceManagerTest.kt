package app.tsunayomi.source.kotatsu

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import app.tsunayomi.source.api.Source
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class KotatsuSourceManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockAssetManager = mockk<AssetManager>(relaxed = true)
    private val mockPrefs = mockk<SharedPreferences>(relaxed = true)
    private val mockClient = OkHttpClient()

    private val testAllowlistJson = """
        [
            {
                "name": "MANGACHAN",
                "locale": "ru",
                "domain": "manga-chan.me",
                "verifiedAt": "2026-10-24T12:00:00Z"
            },
            {
                "name": "LIKEMANGA",
                "locale": "en",
                "domain": "likemanga.ink",
                "verifiedAt": "2026-10-24T12:00:00Z"
            },
            {
                "name": "KLZ9",
                "locale": "ja",
                "domain": "klz9.com",
                "verifiedAt": "2026-10-24T12:00:00Z",
                "isNsfw": true
            }
        ]
    """.trimIndent()

    @Test
    fun `loads allowlist and filters by language and nsfw`() = runTest {
        val job = kotlinx.coroutines.Job()
        val appScope = kotlinx.coroutines.CoroutineScope(testScheduler + job)
        try {
            every { mockContext.assets } returns mockAssetManager
            every { mockAssetManager.open("allowlist.json") } answers {
                ByteArrayInputStream(testAllowlistJson.toByteArray())
            }
            every { mockContext.getSharedPreferences("extensions", Context.MODE_PRIVATE) } returns mockPrefs
            every { mockPrefs.getStringSet("languages", null) } returns setOf("en", "ru", "ja")
            every { mockPrefs.getBoolean("show_nsfw", false) } returns false

            val manager = KotatsuSourceManager(mockContext, appScope, mockClient)
            manager.awaitLoaded()

            val allSources = manager.sources.value
            assertEquals(3, allSources.size)

            // With show_nsfw = false, KLZ9 (hentai) should be hidden from browsable
            val browsableWithoutNsfw = manager.browsable.first { it.isNotEmpty() }
            val namesWithoutNsfw = browsableWithoutNsfw.map { it.name }
            assertTrue(namesWithoutNsfw.contains("Манга-тян"))
            assertTrue(namesWithoutNsfw.contains("LikeManga"))
            assertFalse(namesWithoutNsfw.contains("KLZ9"))
        } finally {
            job.cancel()
        }
    }
}

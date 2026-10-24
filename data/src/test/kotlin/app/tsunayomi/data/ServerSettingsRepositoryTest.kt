package app.tsunayomi.data

import app.tsunayomi.core.datastore.ServerPreferencesDataSource
import app.tsunayomi.core.model.OpenAiConfig
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ServerSettingsRepositoryTest {

    private val preferences: ServerPreferencesDataSource = mockk(relaxed = true)
    private val client: OkHttpClient = OkHttpClient()

    private lateinit var repository: ServerSettingsRepository

    @BeforeEach
    fun setup() {
        repository = ServerSettingsRepository(
            preferences = preferences,
            client = client,
        )
    }

    @Test
    fun `validateGemini fails when api key is blank`() = runTest {
        val result = repository.validateGemini("   ")
        assertTrue(result.isFailure)
    }

    @Test
    fun `validateDeepL fails when api key is blank`() = runTest {
        val result = repository.validateDeepL("")
        assertTrue(result.isFailure)
    }

    @Test
    fun `fetchOpenRouterFreeModels returns fallback models when offline or error`() = runTest {
        // Without internet or with invalid connection, fallback stock models are returned
        val result = repository.fetchOpenRouterFreeModels()
        assertTrue(result.isSuccess)
        val models = result.getOrThrow()
        assertTrue(models.contains("nvidia/nemotron-3-ultra-550b-a55b:free"))
        assertTrue(models.contains("google/gemma-4-31b-it:free"))
        assertTrue(models.contains("thinkingmachines/inkling:free"))
    }

    @Test
    fun `default openrouter free models are properly configured`() {
        assertEquals(3, OpenAiConfig.DEFAULT_OPENROUTER_FREE_MODELS.size)
        assertEquals("nvidia/nemotron-3-ultra-550b-a55b:free", OpenAiConfig.DEFAULT_OPENROUTER_FREE_MODELS[0])
        assertEquals("google/gemma-4-31b-it:free", OpenAiConfig.DEFAULT_OPENROUTER_FREE_MODELS[1])
        assertEquals("thinkingmachines/inkling:free", OpenAiConfig.DEFAULT_OPENROUTER_FREE_MODELS[2])
    }
}

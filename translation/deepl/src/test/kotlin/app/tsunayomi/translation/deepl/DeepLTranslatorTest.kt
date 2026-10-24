package app.tsunayomi.translation.deepl

import app.tsunayomi.core.model.DeepLConfig
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.translation.api.SourceText
import app.tsunayomi.translation.api.TranslationContext
import app.tsunayomi.translation.api.TranslationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeepLTranslatorTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.close()
    }

    @Test
    fun `translate sends texts and parses translations`() = runTest {
        val json = """
            {
              "translations": [
                { "detected_source_language": "EN", "text": "Привет" },
                { "detected_source_language": "EN", "text": "Мир" }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(200).body(json).build())

        val config = DeepLConfig(apiKey = "mock-key:fx")
        val translator = DeepLTranslator(
            client = client,
            configs = flowOf(config),
            baseUrlOverride = server.url("/v2").toString(),
        )

        val batch = translator.translate(
            texts = listOf(SourceText("Hello"), SourceText("World")),
            sourceLang = "en",
            targetLang = "ru",
            context = TranslationContext(),
        )

        assertEquals(listOf("Привет", "Мир"), batch.translations)

        val request = server.takeRequest()
        assertEquals("/v2/translate", request.url.encodedPath)
        assertEquals("DeepL-Auth-Key mock-key:fx", request.headers["Authorization"])
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains("\"target_lang\":\"RU\""))
        assertTrue(body.contains("\"Hello\""))
        assertTrue(body.contains("\"World\""))
    }

    @Test
    fun `free tier vs pro tier endpoint detection`() {
        val freeConfig = DeepLConfig(apiKey = "12345:fx")
        assertTrue(freeConfig.isFreeTier)
        assertEquals(DeepLConfig.FREE_ENDPOINT, freeConfig.endpoint)

        val proConfig = DeepLConfig(apiKey = "12345")
        assertTrue(!proConfig.isFreeTier)
        assertEquals(DeepLConfig.PRO_ENDPOINT, proConfig.endpoint)
    }

    @Test
    fun `blank api key throws MissingApiKey`() = runTest {
        val translator = DeepLTranslator(
            client = client,
            configs = flowOf(DeepLConfig(apiKey = "")),
            baseUrlOverride = server.url("/v2").toString(),
        )

        val ex = assertThrows(TranslationException.MissingApiKey::class.java) {
            runBlocking {
                translator.translate(
                    texts = listOf(SourceText("Hello")),
                    sourceLang = "en",
                    targetLang = "ru",
                )
            }
        }
        assertEquals(TranslationEngine.DeepL, ex.engine)
    }

    @Test
    fun `403 throws InvalidApiKey`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).body("Forbidden").build())

        val translator = DeepLTranslator(
            client = client,
            configs = flowOf(DeepLConfig(apiKey = "bad-key:fx")),
            baseUrlOverride = server.url("/v2").toString(),
        )

        val ex = assertThrows(TranslationException.InvalidApiKey::class.java) {
            runBlocking {
                translator.translate(
                    texts = listOf(SourceText("Hello")),
                    sourceLang = "en",
                    targetLang = "ru",
                )
            }
        }
        assertEquals(TranslationEngine.DeepL, ex.engine)
    }

    @Test
    fun `456 throws QuotaExceeded`() = runTest {
        server.enqueue(MockResponse.Builder().code(456).body("Quota Exceeded").build())

        val translator = DeepLTranslator(
            client = client,
            configs = flowOf(DeepLConfig(apiKey = "good-key:fx")),
            baseUrlOverride = server.url("/v2").toString(),
        )

        val ex = assertThrows(TranslationException.QuotaExceeded::class.java) {
            runBlocking {
                translator.translate(
                    texts = listOf(SourceText("Hello")),
                    sourceLang = "en",
                    targetLang = "ru",
                )
            }
        }
        assertEquals(TranslationEngine.DeepL, ex.engine)
    }

    @Test
    fun `getUsage fetches character count and limit`() = runTest {
        val json = """
            {
              "character_count": 125000,
              "character_limit": 500000
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(200).body(json).build())

        val translator = DeepLTranslator(
            client = client,
            configs = flowOf(DeepLConfig(apiKey = "test-key:fx")),
            baseUrlOverride = server.url("/v2").toString(),
        )

        val usage = translator.getUsage()
        assertEquals(125000L, usage.characterCount)
        assertEquals(500000L, usage.characterLimit)
        assertEquals(25, usage.percentage)

        val request = server.takeRequest()
        assertEquals("/v2/usage", request.url.encodedPath)
        assertEquals("DeepL-Auth-Key test-key:fx", request.headers["Authorization"])
    }

    @Test
    fun `target language normalizer formats codes correctly`() {
        assertEquals("EN-US", DeepLTranslator.normalizeTargetLang("en"))
        assertEquals("PT-BR", DeepLTranslator.normalizeTargetLang("pt"))
        assertEquals("ZH-HANS", DeepLTranslator.normalizeTargetLang("zh"))
        assertEquals("RU", DeepLTranslator.normalizeTargetLang("ru"))
        assertEquals("JA", DeepLTranslator.normalizeTargetLang("ja"))
    }
}

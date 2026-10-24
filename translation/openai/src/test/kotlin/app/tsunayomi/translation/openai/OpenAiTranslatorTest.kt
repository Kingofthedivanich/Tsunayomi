package app.tsunayomi.translation.openai

import app.tsunayomi.core.model.OpenAiConfig
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.translation.api.SourceText
import app.tsunayomi.translation.api.TranslationContext
import app.tsunayomi.translation.api.TranslationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenAiTranslatorTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val configFlow = MutableStateFlow(OpenAiConfig())
    private lateinit var translator: OpenAiTranslator

    @BeforeEach
    fun setUp() {
        server.start()
        configFlow.value = OpenAiConfig(
            baseUrl = server.url("/v1").toString(),
            apiKey = "sk-test-key",
            model = "deepseek-chat",
        )
        translator = OpenAiTranslator(client, configFlow)
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `translate parses json response and extracts translations and glossary`() = runTest {
        val json = """
            {
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": "{\"translations\": [{\"id\": 0, \"text\": \"Привет!\"}, {\"id\": 1, \"text\": \"Как дела?\"}], \"glossary\": [{\"source\": \"Hero\", \"target\": \"Герой\"}]}"
                        }
                    }
                ]
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(json).build())

        val texts = listOf(
            SourceText("Hello!", page = 1),
            SourceText("How are you?", page = 1),
        )
        val result = translator.translate(texts, sourceLang = "en", targetLang = "ru")
        val recorded = server.takeRequest()

        assertEquals("/v1/chat/completions", recorded.url.encodedPath)
        assertEquals("Bearer sk-test-key", recorded.headers["Authorization"])
        assertEquals(listOf("Привет!", "Как дела?"), result.translations)
        assertEquals(mapOf("Hero" to "Герой"), result.newTerms)
    }

    @Test
    fun `translate handles json wrapped in markdown code fence`() = runTest {
        val json = """
            {
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": "```json\n{\"translations\": [{\"id\": 0, \"text\": \"Ура!\"}], \"glossary\": []}\n```"
                        }
                    }
                ]
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(json).build())

        val texts = listOf(SourceText("Yay!", page = 1))
        val result = translator.translate(texts, sourceLang = "en", targetLang = "ru")

        assertEquals(listOf("Ура!"), result.translations)
    }

    @Test
    fun `translate works without Authorization header when apiKey is empty`() = runTest {
        configFlow.value = OpenAiConfig(
            baseUrl = server.url("/v1").toString(),
            apiKey = "",
            model = "qwen2.5:7b",
        )
        val json = """
            {
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": "{\"translations\": [{\"id\": 0, \"text\": \"Локальный ответ\"}]}"
                        }
                    }
                ]
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(json).build())

        val texts = listOf(SourceText("Local input", page = 1))
        val result = translator.translate(texts, sourceLang = "en", targetLang = "ru")
        val recorded = server.takeRequest()

        assertNull(recorded.headers["Authorization"])
        assertEquals(listOf("Локальный ответ"), result.translations)
    }

    @Test
    fun `translate throws InvalidApiKey on 401 or 403`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("""{"error": {"message": "Invalid API key"}}""").build())

        val ex = assertThrows(TranslationException.InvalidApiKey::class.java) {
            kotlinx.coroutines.runBlocking {
                translator.translate(listOf(SourceText("Hi")), "en", "ru")
            }
        }
        assertEquals(TranslationEngine.OpenAiCompatible, ex.engine)
    }

    @Test
    fun `translate throws QuotaExceeded on 429`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).body("""{"error": {"message": "Rate limit exceeded"}}""").build())

        val ex = assertThrows(TranslationException.QuotaExceeded::class.java) {
            kotlinx.coroutines.runBlocking {
                translator.translate(listOf(SourceText("Hi")), "en", "ru")
            }
        }
        assertEquals(TranslationEngine.OpenAiCompatible, ex.engine)
    }

    @Test
    fun `cacheTag reflects baseUrl and model`() = runTest {
        val tag = translator.cacheTag()
        assertTrue(tag.contains("deepseek-chat"))
    }
}

package app.tsunayomi.translation.gemini

import app.tsunayomi.core.model.GeminiConfig
import app.tsunayomi.translation.api.SourceText
import app.tsunayomi.translation.api.TranslationContext
import app.tsunayomi.translation.api.TranslationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GeminiTranslatorTest {
    private val server = MockWebServer()
    private val config = MutableStateFlow(GeminiConfig(apiKey = "test-key", models = listOf("test-model")))
    private var clock = 1_800_000_000_000L
    private lateinit var translator: GeminiTranslator

    @BeforeEach
    fun setUp() {
        server.start()
        translator = GeminiTranslator(OkHttpClient(), config, server.url("/"), ModelQuota { clock })
    }

    @AfterEach
    fun tearDown() = server.close()

    private fun texts(vararg values: String) = values.map { SourceText(it) }

    private fun answer(itemsJson: String) {
        val escaped = Json.encodeToString(String.serializer(), itemsJson)
        server.enqueue(
            MockResponse.Builder()
                .body("""{"candidates":[{"content":{"parts":[{"text":$escaped}],"role":"model"},"finishReason":"STOP"}]}""")
                .build(),
        )
    }

    @Test
    fun `sends one request with numbered items and parses the answer`() = runTest {
        answer("""{"translations":[{"id":1,"text":"Мир"},{"id":0,"text":"Привет"}],"glossary":[{"source":"Kenta","target":"Кента"}]}""")

        val result = translator.translate(
            listOf(SourceText("Hello", page = 1), SourceText("World", page = 2)),
            sourceLang = "en",
            targetLang = "ru",
            context = TranslationContext(glossary = mapOf("Hana" to "Хана")),
        )

        assertEquals(listOf("Привет", "Мир"), result.translations)
        assertEquals(mapOf("Kenta" to "Кента"), result.newTerms)
        val request = server.takeRequest()
        assertEquals("/v1beta/models/test-model:generateContent", request.url.encodedPath)
        assertEquals("test-key", request.headers["x-goog-api-key"])
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        val userText = body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("""[{"id":0,"page":1,"text":"Hello"},{"id":1,"page":2,"text":"World"}]""", userText)
        assertEquals(
            "application/json",
            body["generationConfig"]!!.jsonObject["responseMimeType"]!!.jsonPrimitive.content,
        )
        val system = body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue("from English to Russian" in system)
        assertTrue("- Hana → Хана" in system)
    }

    @Test
    fun `missing items keep the original text`() = runTest {
        answer("""{"translations":[{"id":0,"text":"Привет"}]}""")
        val result = translator.translate(texts("Hello", "World"), "en", "ru")
        assertEquals(listOf("Привет", "World"), result.translations)
        assertTrue(result.newTerms.isEmpty())
    }

    @Test
    fun `no key fails before any request`() = runTest {
        config.value = GeminiConfig(apiKey = "")
        assertThrows<TranslationException.MissingApiKey> { translator.translate(texts("x"), null, "ru") }
        assertEquals(0, server.requestCount)
    }

    private fun quotaError(quotaId: String, retry: String? = null) = MockResponse.Builder().code(429).body(
        """{"error":{"code":429,"message":"Resource exhausted","details":[
            {"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaId":"$quotaId"}]}
            ${retry?.let { """,{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"$it"}""" } ?: ""}
        ]}}""",
    ).build()

    private fun ok() = MockResponse.Builder()
        .body("""{"candidates":[{"content":{"parts":[{"text":"{\"translations\":[{\"id\":0,\"text\":\"ок\"}]}"}]}}]}""")
        .build()

    @Test
    fun `daily quota moves to the next model and skips the exhausted one until the reset`() = runTest {
        config.value = GeminiConfig(apiKey = "k", models = listOf("first", "second"))
        server.enqueue(quotaError("GenerateRequestsPerDayPerProjectPerModel-FreeTier"))
        server.enqueue(ok())
        server.enqueue(ok())

        assertEquals(listOf("ок"), translator.translate(texts("x"), null, "ru").translations)
        assertTrue(server.takeRequest().url.encodedPath.endsWith("/first:generateContent"))
        assertTrue(server.takeRequest().url.encodedPath.endsWith("/second:generateContent"))

        // A later call goes straight to the second model.
        translator.translate(texts("y"), null, "ru")
        assertTrue(server.takeRequest().url.encodedPath.endsWith("/second:generateContent"))

        // After the next Pacific midnight the first model is tried again.
        clock += 25 * 60 * 60 * 1000L
        server.enqueue(ok())
        translator.translate(texts("z"), null, "ru")
        assertTrue(server.takeRequest().url.encodedPath.endsWith("/first:generateContent"))
    }

    @Test
    fun `per minute quota blocks the model only for the suggested delay`() = runTest {
        config.value = GeminiConfig(apiKey = "k", models = listOf("first", "second"))
        server.enqueue(quotaError("GenerateContentInputTokensPerModelPerMinute-FreeTier", retry = "20s"))
        server.enqueue(ok())
        translator.translate(texts("x"), null, "ru")
        server.takeRequest()
        server.takeRequest()

        clock += 21_000
        server.enqueue(ok())
        translator.translate(texts("y"), null, "ru")
        assertTrue(server.takeRequest().url.encodedPath.endsWith("/first:generateContent"))
    }

    @Test
    fun `an unknown model id is skipped`() = runTest {
        config.value = GeminiConfig(apiKey = "k", models = listOf("typo-model", "second"))
        server.enqueue(MockResponse.Builder().code(404).body("""{"error":{"message":"not found"}}""").build())
        server.enqueue(ok())
        assertEquals(listOf("ок"), translator.translate(texts("x"), null, "ru").translations)
    }

    @Test
    fun `gemma gets no JSON mode and fenced answers still parse`() = runTest {
        config.value = GeminiConfig(apiKey = "k", models = listOf("gemma-4-31b-it"))
        server.enqueue(
            MockResponse.Builder()
                .body("""{"candidates":[{"content":{"parts":[{"text":"```json\n{\"translations\":[{\"id\":0,\"text\":\"ок\"}]}\n```"}]}}]}""")
                .build(),
        )

        assertEquals(listOf("ок"), translator.translate(texts("x"), null, "ru").translations)
        val generationConfig = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject["generationConfig"]!!.jsonObject
        assertTrue("responseMimeType" !in generationConfig)
        assertTrue("responseSchema" !in generationConfig)
    }

    @Test
    fun `cache tag follows the model chain`() = runTest {
        config.value = GeminiConfig(apiKey = "k", models = listOf("a", "b"))
        assertEquals("a+b", translator.cacheTag())
    }

    @Test
    fun `quota and bad key map to typed errors`() = runTest {
        server.enqueue(quotaError("GenerateRequestsPerDayPerProjectPerModel-FreeTier"))
        assertThrows<TranslationException.QuotaExceeded> { translator.translate(texts("x"), null, "ru") }

        clock += 25 * 60 * 60 * 1000L // past the daily reset, so the model is tried again
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":{"message":"API key not valid. Please pass a valid API key."}}""").build())
        assertThrows<TranslationException.InvalidApiKey> { translator.translate(texts("x"), null, "ru") }
    }

    @Test
    fun `blocked answers explain why`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"promptFeedback":{"blockReason":"SAFETY"}}""").build())
        val error = assertThrows<TranslationException.Engine> { translator.translate(texts("x"), null, "ru") }
        assertTrue("SAFETY" in error.message!!)
    }

    @Test
    fun `unknown source language asks the model to detect it`() {
        assertTrue("the source language (detect it)" in GeminiTranslator.systemPrompt(null, "ru"))
    }
}

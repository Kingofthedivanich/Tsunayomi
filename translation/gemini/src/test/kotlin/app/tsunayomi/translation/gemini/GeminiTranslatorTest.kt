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
    private val config = MutableStateFlow(GeminiConfig(apiKey = "test-key", model = "test-model"))
    private lateinit var translator: GeminiTranslator

    @BeforeEach
    fun setUp() {
        server.start()
        translator = GeminiTranslator(OkHttpClient(), config, baseUrl = server.url("/"))
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

    @Test
    fun `quota and bad key map to typed errors`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).body("""{"error":{"message":"Resource exhausted"}}""").build())
        assertThrows<TranslationException.QuotaExceeded> { translator.translate(texts("x"), null, "ru") }

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

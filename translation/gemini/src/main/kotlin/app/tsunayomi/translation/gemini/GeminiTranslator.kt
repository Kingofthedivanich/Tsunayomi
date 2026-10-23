package app.tsunayomi.translation.gemini

import app.tsunayomi.core.model.GeminiConfig
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.core.network.await
import app.tsunayomi.translation.api.TranslationException
import app.tsunayomi.translation.api.Translator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale

/**
 * Translation with the user's own Gemini API key (free tier is enough for personal reading).
 * A whole page goes in one request as numbered items; the model answers with JSON keyed by the same ids.
 */
class GeminiTranslator(
    private val client: OkHttpClient,
    private val configs: Flow<GeminiConfig>,
    private val baseUrl: HttpUrl = BASE_URL.toHttpUrl(),
) : Translator {
    override val engine: TranslationEngine = TranslationEngine.Gemini
    override val cacheVersion: Int = PROMPT_VERSION

    override suspend fun translate(texts: List<String>, sourceLang: String?, targetLang: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        val config = configs.first()
        if (config.apiKey.isBlank()) throw TranslationException.MissingApiKey(engine)

        val url = baseUrl.newBuilder()
            .addPathSegments("v1beta/models")
            .addPathSegment("${config.model}:generateContent")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", config.apiKey)
            .post(requestBody(texts, sourceLang, targetLang).toRequestBody(JSON_TYPE))
            .build()

        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw TranslationException.Network(e)
        }
        val body = response.use { it.body.string() }
        when {
            response.code == 429 -> throw TranslationException.QuotaExceeded(engine)
            response.code == 401 || response.code == 403 -> throw TranslationException.InvalidApiKey(engine)
            response.code == 400 && "API key" in body -> throw TranslationException.InvalidApiKey(engine)
            !response.isSuccessful -> throw TranslationException.Engine("Gemini HTTP ${response.code}: ${errorMessage(body)}")
        }
        return parse(body, texts)
    }

    private fun requestBody(texts: List<String>, sourceLang: String?, targetLang: String): String {
        val items = json.encodeToString(texts.mapIndexed { index, text -> Item(index, text) })
        return buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", systemPrompt(sourceLang, targetLang)) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", items) } }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.3)
                put("responseMimeType", "application/json")
                putJsonObject("responseSchema") {
                    put("type", "ARRAY")
                    putJsonObject("items") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("id") { put("type", "INTEGER") }
                            putJsonObject("text") { put("type", "STRING") }
                        }
                        putJsonArray("required") {
                            add("id")
                            add("text")
                        }
                    }
                }
            }
        }.toString()
    }

    private fun parse(body: String, originals: List<String>): List<String> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw TranslationException.Engine("Gemini returned malformed JSON")
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        if (candidate == null) {
            val reason = root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.content
            throw TranslationException.Engine("Gemini returned no answer${reason?.let { " (blocked: $it)" }.orEmpty()}")
        }
        val text = candidate["content"]?.jsonObject?.get("parts")?.jsonArray
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
            ?: throw TranslationException.Engine("Gemini answer has no text")
        val items = runCatching { json.decodeFromString<List<Item>>(text) }.getOrElse {
            throw TranslationException.Engine("Gemini answer is not the expected JSON", it)
        }
        val byId = items.associate { it.id to it.text }
        // A skipped item keeps its original text rather than shifting every later bubble.
        return originals.mapIndexed { index, original -> byId[index]?.takeIf { it.isNotBlank() } ?: original }
    }

    private fun errorMessage(body: String): String = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
    }.getOrDefault(body.take(200))

    @Serializable
    private data class Item(val id: Int, val text: String)

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/"
        private val JSON_TYPE = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        /** Bumped whenever the prompt changes, so cached translations made with the old one are redone. */
        const val PROMPT_VERSION = 1

        internal fun systemPrompt(sourceLang: String?, targetLang: String): String {
            val target = languageName(targetLang)
            val source = sourceLang?.let(::languageName) ?: "the source language (detect it)"
            return """
                You translate speech bubbles and captions from manga, manhwa, manhua and comics.
                The user sends a JSON array of items {"id", "text"} from one page, in reading order.
                Translate every item from $source to $target.
                - Sound natural and concise, in each character's voice; bubbles have little room.
                - Keep names, honorifics and terms consistent across items.
                - The text comes from OCR: fix broken line breaks and obvious recognition errors from context.
                - Keep sound effects short. Return page numbers, credits and noise unchanged.
                - Never add notes or explanations.
                Answer with a JSON array containing one {"id", "text"} object per input item, with the same ids.
            """.trimIndent()
        }

        private fun languageName(tag: String): String =
            Locale.forLanguageTag(tag).getDisplayLanguage(Locale.ENGLISH).ifEmpty { tag }
    }
}

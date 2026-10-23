package app.tsunayomi.translation.gemini

import app.tsunayomi.core.model.GeminiConfig
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.core.network.await
import app.tsunayomi.translation.api.SourceText
import app.tsunayomi.translation.api.TranslationBatch
import app.tsunayomi.translation.api.TranslationContext
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
 * Several pages go in one request as numbered items tagged with their page; the model answers with JSON keyed
 * by the same ids, plus names and terms it saw for the first time, which the caller keeps as a glossary.
 */
class GeminiTranslator(
    private val client: OkHttpClient,
    private val configs: Flow<GeminiConfig>,
    private val baseUrl: HttpUrl = BASE_URL.toHttpUrl(),
) : Translator {
    override val engine: TranslationEngine = TranslationEngine.Gemini
    override val cacheVersion: Int = PROMPT_VERSION
    override val batchesPages: Boolean = true

    override suspend fun translate(
        texts: List<SourceText>,
        sourceLang: String?,
        targetLang: String,
        context: TranslationContext,
    ): TranslationBatch {
        if (texts.isEmpty()) return TranslationBatch(emptyList())
        val config = configs.first()
        if (config.apiKey.isBlank()) throw TranslationException.MissingApiKey(engine)

        val url = baseUrl.newBuilder()
            .addPathSegments("v1beta/models")
            .addPathSegment("${config.model}:generateContent")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", config.apiKey)
            .post(requestBody(texts, sourceLang, targetLang, context).toRequestBody(JSON_TYPE))
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

    private fun requestBody(texts: List<SourceText>, sourceLang: String?, targetLang: String, context: TranslationContext): String {
        val items = json.encodeToString(texts.mapIndexed { index, text -> SourceItem(index, text.page, text.text) })
        return buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", systemPrompt(sourceLang, targetLang, context.glossary)) } }
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
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("translations") {
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
                        putJsonObject("glossary") {
                            put("type", "ARRAY")
                            putJsonObject("items") {
                                put("type", "OBJECT")
                                putJsonObject("properties") {
                                    putJsonObject("source") { put("type", "STRING") }
                                    putJsonObject("target") { put("type", "STRING") }
                                }
                                putJsonArray("required") {
                                    add("source")
                                    add("target")
                                }
                            }
                        }
                    }
                    putJsonArray("required") { add("translations") }
                }
            }
        }.toString()
    }

    private fun parse(body: String, originals: List<SourceText>): TranslationBatch {
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
        val answer = runCatching { json.decodeFromString<Answer>(text) }.getOrElse {
            throw TranslationException.Engine("Gemini answer is not the expected JSON", it)
        }
        val byId = answer.translations.associate { it.id to it.text }
        return TranslationBatch(
            // A skipped item keeps its original text rather than shifting every later bubble.
            translations = originals.mapIndexed { index, original -> byId[index]?.takeIf { it.isNotBlank() } ?: original.text },
            newTerms = answer.glossary
                .filter { it.source.isNotBlank() && it.target.isNotBlank() }
                .associate { it.source.trim() to it.target.trim() },
        )
    }

    private fun errorMessage(body: String): String = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
    }.getOrDefault(body.take(200))

    @Serializable
    private data class SourceItem(val id: Int, val page: Int, val text: String)

    @Serializable
    private data class Item(val id: Int, val text: String)

    @Serializable
    private data class Term(val source: String, val target: String)

    @Serializable
    private data class Answer(val translations: List<Item>, val glossary: List<Term> = emptyList())

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/"
        private val JSON_TYPE = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        /** Bumped whenever the prompt changes, so cached translations made with the old one are redone. */
        const val PROMPT_VERSION = 2

        /** Keeps the prompt bounded for long series; the oldest terms are the main cast and come first. */
        private const val MAX_GLOSSARY_TERMS = 300

        internal fun systemPrompt(sourceLang: String?, targetLang: String, glossary: Map<String, String> = emptyMap()): String {
            val target = languageName(targetLang)
            val source = sourceLang?.let(::languageName) ?: "the source language (detect it)"
            val established = if (glossary.isEmpty()) {
                ""
            } else {
                "\nEstablished translations — always use them exactly:\n" +
                    glossary.entries.take(MAX_GLOSSARY_TERMS).joinToString("\n") { (from, to) -> "- $from → $to" } + "\n"
            }
            return """
                You translate speech bubbles and captions from manga, manhwa, manhua and comics.
                The user sends a JSON array of items {"id", "page", "text"} from consecutive pages of one chapter,
                in reading order. Use the whole batch as context: it is one continuous story.
                Translate every item from $source to $target.
                - Sound natural and concise, in each character's voice; bubbles have little room.
                - Keep names, honorifics and terms consistent across items and pages.
                - The text comes from OCR: fix broken line breaks and obvious recognition errors from context.
                - Keep sound effects short. Return page numbers, credits and noise unchanged.
                - Never add notes or explanations.
                $established
                Answer with a JSON object:
                - "translations": one {"id", "text"} per input item, with the same ids;
                - "glossary": character names, places, titles and special terms that appear in this batch and are
                  not listed above, as {"source": original spelling, "target": your translation}. Empty if none.
            """.trimIndent()
        }

        private fun languageName(tag: String): String =
            Locale.forLanguageTag(tag).getDisplayLanguage(Locale.ENGLISH).ifEmpty { tag }
    }
}

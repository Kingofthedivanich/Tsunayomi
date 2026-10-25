package app.tsunayomi.translation.openai

import app.tsunayomi.core.model.OpenAiConfig
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
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale

/**
 * Translation using any OpenAI-compatible API (DeepSeek, OpenRouter, Groq, local Ollama/LM Studio, ChatGPT).
 * Multiple pages are submitted in a single request as numbered items, allowing the LLM to maintain story
 * and character voice context while extracting names into the glossary.
 */
class OpenAiTranslator(
    private val client: OkHttpClient,
    private val configs: Flow<OpenAiConfig>,
) : Translator {

    override val engine: TranslationEngine = TranslationEngine.OpenAiCompatible
    override val cacheVersion: Int = PROMPT_VERSION
    override val batchesPages: Boolean = true

    override suspend fun cacheTag(): String {
        val config = configs.first()
        return "${config.baseUrl}+${config.model}"
    }

    override suspend fun translate(
        texts: List<SourceText>,
        sourceLang: String?,
        targetLang: String,
        context: TranslationContext,
    ): TranslationBatch {
        if (texts.isEmpty()) return TranslationBatch(emptyList())
        val config = configs.first()

        val endpoint = "${config.baseUrl.trimEnd('/')}/chat/completions"
        val requestBodyString = buildRequestBody(config.model, texts, sourceLang, targetLang, context)

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(requestBodyString.toRequestBody(JSON_TYPE))

        if (config.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.apiKey.trim()}")
        }

        val response = try {
            client.newCall(requestBuilder.build()).await()
        } catch (e: IOException) {
            throw TranslationException.Network(e)
        }

        val body = response.use { it.body.string() }

        when {
            // 403 from OpenRouter is a region block or a flagged request, not a bad key; it falls through to the
            // generic error below so the provider's message is shown.
            response.code == 401 -> throw TranslationException.InvalidApiKey(engine)
            response.code == 429 -> throw TranslationException.QuotaExceeded(engine)
            !response.isSuccessful -> {
                val errorMsg = errorMessage(body)
                throw TranslationException.Engine("OpenAI HTTP ${response.code}: $errorMsg")
            }
        }

        return parseResponse(body, texts)
    }

    private fun buildRequestBody(
        model: String,
        texts: List<SourceText>,
        sourceLang: String?,
        targetLang: String,
        context: TranslationContext,
    ): String {
        val itemsJson = json.encodeToString(
            texts.mapIndexed { index, text -> SourceItem(index, text.page, text.text) },
        )
        val systemPrompt = systemPrompt(sourceLang, targetLang, context.glossary)

        return buildJsonObject {
            put("model", model)
            put("temperature", 0.3)
            putJsonObject("response_format") {
                put("type", "json_object")
            }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", itemsJson)
                }
            }
        }.toString()
    }

    private fun parseResponse(body: String, originals: List<SourceText>): TranslationBatch {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw TranslationException.Engine("OpenAI returned malformed JSON")

        val choices = root["choices"]?.jsonArray
        val messageContent = choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw TranslationException.Engine("OpenAI response contains no content")

        val jsonStr = extractJson(messageContent)
        val answer = runCatching { json.decodeFromString<Answer>(jsonStr) }.getOrElse {
            throw TranslationException.Engine("OpenAI content is not the expected JSON structure", it)
        }

        val byId = answer.translations.associate { it.id to it.text }
        return TranslationBatch(
            translations = originals.mapIndexed { index, original ->
                byId[index]?.takeIf { it.isNotBlank() } ?: original.text
            },
            newTerms = answer.glossary
                .filter { it.source.isNotBlank() && it.target.isNotBlank() }
                .associate { it.source.trim() to it.target.trim() },
        )
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }

    private fun errorMessage(body: String): String = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
    }.getOrNull() ?: body.take(200)

    @Serializable
    private data class SourceItem(val id: Int, val page: Int, val text: String)

    @Serializable
    private data class Item(val id: Int, val text: String)

    @Serializable
    private data class Term(val source: String, val target: String)

    @Serializable
    private data class Answer(val translations: List<Item>, val glossary: List<Term> = emptyList())

    companion object {
        private val JSON_TYPE = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
        const val PROMPT_VERSION = 1
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
                Answer with only a JSON object, no other text:
                - "translations": one {"id", "text"} per input item, with the same ids;
                - "glossary": character names, places, titles and special terms that appear in this batch and are
                  not listed above, as {"source": original spelling, "target": your translation}. Empty if none.
            """.trimIndent()
        }

        private fun languageName(tag: String): String =
            Locale.forLanguageTag(tag).getDisplayLanguage(Locale.ENGLISH).ifEmpty { tag }
    }
}

package app.tsunayomi.translation.deepl

import app.tsunayomi.core.model.DeepLConfig
import app.tsunayomi.core.model.DeepLUsage
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.core.network.await
import app.tsunayomi.translation.api.SourceText
import app.tsunayomi.translation.api.TranslationBatch
import app.tsunayomi.translation.api.TranslationContext
import app.tsunayomi.translation.api.TranslationException
import app.tsunayomi.translation.api.Translator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale

class DeepLTranslator(
    private val client: OkHttpClient,
    private val configs: Flow<DeepLConfig>,
    private val baseUrlOverride: String? = null,
) : Translator {

    override val engine: TranslationEngine = TranslationEngine.DeepL
    override val cacheVersion: Int = 1
    override val batchesPages: Boolean = false

    override suspend fun cacheTag(): String {
        val config = configs.first()
        return if (config.isFreeTier) "deepl_free" else "deepl_pro"
    }

    override suspend fun translate(
        texts: List<SourceText>,
        sourceLang: String?,
        targetLang: String,
        context: TranslationContext,
    ): TranslationBatch {
        if (texts.isEmpty()) return TranslationBatch(emptyList())

        val config = configs.first()
        if (config.apiKey.isBlank()) {
            throw TranslationException.MissingApiKey(engine)
        }

        val base = baseUrlOverride ?: config.endpoint
        val endpoint = "${base.trimEnd('/')}/translate"
        val normalizedTarget = normalizeTargetLang(targetLang)
        val normalizedSource = sourceLang?.let(::normalizeSourceLang)

        // DeepL supports up to 50 text elements per API call
        val allTranslations = mutableListOf<String>()
        val chunks = texts.chunked(50)

        for (chunk in chunks) {
            val requestBodyJson = buildJsonObject {
                putJsonArray("text") {
                    for (item in chunk) {
                        add(item.text)
                    }
                }
                put("target_lang", normalizedTarget)
                if (!normalizedSource.isNullOrBlank()) {
                    put("source_lang", normalizedSource)
                }
            }.toString()

            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "DeepL-Auth-Key ${config.apiKey.trim()}")
                .header("Content-Type", "application/json")
                .post(requestBodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val responseBody = try {
                client.newCall(request).await().use { response ->
                    when (response.code) {
                        200 -> response.body?.string().orEmpty()
                        403 -> throw TranslationException.InvalidApiKey(engine)
                        456 -> throw TranslationException.QuotaExceeded(engine)
                        429 -> throw TranslationException.Engine("DeepL rate limit reached (HTTP 429)")
                        else -> {
                            val errorBody = response.body?.string().orEmpty()
                            throw TranslationException.Engine("DeepL error ${response.code}: $errorBody")
                        }
                    }
                }
            } catch (e: IOException) {
                throw TranslationException.Network(e)
            }

            val parsed = json.decodeFromString(DeepLResponse.serializer(), responseBody)
            for (translation in parsed.translations) {
                allTranslations.add(translation.text)
            }
        }

        return TranslationBatch(translations = allTranslations)
    }

    suspend fun getUsage(): DeepLUsage {
        val config = configs.first()
        if (config.apiKey.isBlank()) {
            throw TranslationException.MissingApiKey(engine)
        }
        val base = baseUrlOverride ?: config.endpoint
        val endpoint = "${base.trimEnd('/')}/usage"

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "DeepL-Auth-Key ${config.apiKey.trim()}")
            .get()
            .build()

        val responseBody = try {
            client.newCall(request).await().use { response ->
                when (response.code) {
                    200 -> response.body?.string().orEmpty()
                    403 -> throw TranslationException.InvalidApiKey(engine)
                    456 -> throw TranslationException.QuotaExceeded(engine)
                    429 -> throw TranslationException.Engine("DeepL rate limit reached (HTTP 429)")
                    else -> throw TranslationException.Engine("DeepL error ${response.code}")
                }
            }
        } catch (e: IOException) {
            throw TranslationException.Network(e)
        }

        val parsed = json.decodeFromString(DeepLUsageResponse.serializer(), responseBody)
        return DeepLUsage(
            characterCount = parsed.characterCount,
            characterLimit = parsed.characterLimit,
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun normalizeTargetLang(lang: String): String {
            val lower = lang.lowercase(Locale.ROOT).trim()
            return when (lower) {
                "en" -> "EN-US"
                "pt" -> "PT-BR"
                "zh" -> "ZH-HANS"
                else -> lower.uppercase(Locale.ROOT)
            }
        }

        fun normalizeSourceLang(lang: String): String {
            val lower = lang.lowercase(Locale.ROOT).trim()
            return when (lower) {
                "en" -> "EN"
                "pt" -> "PT"
                "zh" -> "ZH"
                else -> lower.uppercase(Locale.ROOT)
            }
        }
    }
}

@Serializable
private data class DeepLResponse(
    val translations: List<DeepLTranslation> = emptyList(),
)

@Serializable
private data class DeepLTranslation(
    @SerialName("detected_source_language") val detectedSourceLanguage: String? = null,
    val text: String,
)

@Serializable
private data class DeepLUsageResponse(
    @SerialName("character_count") val characterCount: Long = 0L,
    @SerialName("character_limit") val characterLimit: Long = 500_000L,
)

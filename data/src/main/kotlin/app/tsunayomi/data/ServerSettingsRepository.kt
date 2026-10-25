package app.tsunayomi.data

import app.tsunayomi.core.datastore.ServerPreferencesDataSource
import app.tsunayomi.core.model.DeepLConfig
import app.tsunayomi.core.model.DeepLUsage
import app.tsunayomi.core.model.GeminiConfig
import app.tsunayomi.core.model.KomgaConfig
import app.tsunayomi.core.model.OpenAiConfig
import app.tsunayomi.core.network.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerSettingsRepository @Inject constructor(
    private val preferences: ServerPreferencesDataSource,
    private val client: OkHttpClient,
) {
    val komga: Flow<KomgaConfig?> = preferences.komga

    suspend fun setKomga(config: KomgaConfig?) = preferences.setKomga(config)

    val gemini: Flow<GeminiConfig> = preferences.gemini

    suspend fun setGemini(config: GeminiConfig) = preferences.setGemini(config)

    val openAi: Flow<OpenAiConfig> = preferences.openAi

    suspend fun setOpenAi(config: OpenAiConfig) = preferences.setOpenAi(config)

    val deepL: Flow<DeepLConfig> = preferences.deepL

    suspend fun setDeepL(config: DeepLConfig) = preferences.setDeepL(config)

    suspend fun fetchDeepLUsage(): Result<DeepLUsage> = withContext(Dispatchers.IO) {
        val config = preferences.deepL.first()
        validateDeepL(config.apiKey)
    }

    suspend fun validateDeepL(apiKey: String): Result<DeepLUsage> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(ApiCheckException(ApiCheckException.Reason.NoKey))
        }
        val isFree = apiKey.trim().endsWith(":fx")
        val endpoint = if (isFree) DeepLConfig.FREE_ENDPOINT else DeepLConfig.PRO_ENDPOINT
        try {
            val request = Request.Builder()
                .url("${endpoint.trimEnd('/')}/usage")
                .header("Authorization", "DeepL-Auth-Key ${apiKey.trim()}")
                .get()
                .build()

            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    val reason = when (response.code) {
                        401, 403 -> ApiCheckException.Reason.InvalidKey
                        429, 456 -> ApiCheckException.Reason.QuotaExceeded
                        else -> ApiCheckException.Reason.Http
                    }
                    return@withContext Result.failure(ApiCheckException(reason, "HTTP ${response.code}"))
                }
                val body = response.body.string()
                val json = Json { ignoreUnknownKeys = true }
                val obj = json.parseToJsonElement(body).jsonObject
                val count = obj["character_count"]?.jsonPrimitive?.longOrNull ?: 0L
                val limit = obj["character_limit"]?.jsonPrimitive?.longOrNull ?: 500_000L
                Result.success(DeepLUsage(characterCount = count, characterLimit = limit))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun validateGemini(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(ApiCheckException(ApiCheckException.Reason.NoKey))
        }
        try {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models")
                // In a header, like GeminiTranslator: a key in the URL ends up in logs.
                .header("x-goog-api-key", apiKey.trim())
                .get()
                .build()
            client.newCall(request).await().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val reason = when (response.code) {
                        400, 401, 403 -> ApiCheckException.Reason.InvalidKey
                        429 -> ApiCheckException.Reason.QuotaExceeded
                        else -> ApiCheckException.Reason.Http
                    }
                    Result.failure(ApiCheckException(reason, "HTTP ${response.code}"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun fetchOpenRouterFreeModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        val fallback = OpenAiConfig.DEFAULT_OPENROUTER_FREE_MODELS
        try {
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .get()
                .build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return@withContext Result.success(fallback)
                val body = response.body.string()
                val json = Json { ignoreUnknownKeys = true }
                val root = json.parseToJsonElement(body).jsonObject
                val dataArray = root["data"]?.jsonArray ?: return@withContext Result.success(fallback)
                val fetched = mutableListOf<String>()
                for (element in dataArray) {
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: continue
                    val pricing = obj["pricing"]?.jsonObject
                    val promptCost = pricing?.get("prompt")?.jsonPrimitive?.content
                    val compCost = pricing?.get("completion")?.jsonPrimitive?.content
                    val isZeroPricing = (promptCost == "0" || promptCost == "0.0") && (compCost == "0" || compCost == "0.0")
                    if (id.endsWith(":free") || isZeroPricing) {
                        fetched.add(id)
                    }
                }
                val combined = (fallback + fetched).distinct()
                Result.success(combined)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.success(fallback)
        }
    }

    suspend fun testOpenAiCompatibleModel(
        baseUrl: String,
        apiKey: String,
        model: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "${baseUrl.trimEnd('/')}/chat/completions"
            val bodyJson = buildJsonObject {
                put("model", model)
                put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", "Hi") }) })
                put("max_tokens", 1)
            }.toString()
            val builder = Request.Builder()
                .url(endpoint)
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .header("HTTP-Referer", "https://github.com/Kingofthedivanich/Tsunayomi")
                .header("X-Title", "Tsunayomi")
            if (apiKey.isNotBlank()) {
                builder.header("Authorization", "Bearer ${apiKey.trim()}")
            }
            client.newCall(builder.build()).await().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    // Only 401 means a bad key: OpenRouter answers 403 for a model blocked in the user's region or
                    // a flagged request, so the server's own message is what tells the user what to do.
                    val reason = when (response.code) {
                        401 -> ApiCheckException.Reason.InvalidKey
                        404 -> ApiCheckException.Reason.ModelNotFound
                        429 -> ApiCheckException.Reason.QuotaExceeded
                        else -> ApiCheckException.Reason.Http
                    }
                    val message = errorMessage(response.body.string())
                    Result.failure(ApiCheckException(reason, listOfNotNull("HTTP ${response.code}", message).joinToString(": ")))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

/** The error text of an OpenAI-compatible error body, preferring the upstream provider's own words. */
internal fun errorMessage(body: String): String? = runCatching {
    val error = Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject ?: return null
    val raw = error["metadata"]?.jsonObject?.get("raw")?.jsonPrimitive?.content
    (raw ?: error["message"]?.jsonPrimitive?.content)?.take(300)
}.getOrNull()

/** Why checking an API key or model failed; the settings screen words it in the UI language. */
class ApiCheckException(val reason: Reason, detail: String? = null) : IOException(listOfNotNull(reason.name, detail).joinToString(": ")) {
    enum class Reason { NoKey, InvalidKey, QuotaExceeded, ModelNotFound, Http }
}

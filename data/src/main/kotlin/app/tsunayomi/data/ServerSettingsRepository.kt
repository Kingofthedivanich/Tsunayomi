package app.tsunayomi.data

import app.tsunayomi.core.datastore.ServerPreferencesDataSource
import app.tsunayomi.core.model.DeepLConfig
import app.tsunayomi.core.model.DeepLUsage
import app.tsunayomi.core.model.GeminiConfig
import app.tsunayomi.core.model.KomgaConfig
import app.tsunayomi.core.model.OpenAiConfig
import app.tsunayomi.core.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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
            return@withContext Result.failure(IllegalStateException("API key is not set"))
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
                    val message = when (response.code) {
                        401, 403 -> "Неверный API-ключ DeepL"
                        429, 456 -> "Квота исчерпана"
                        else -> "HTTP ${response.code}: ${response.message}"
                    }
                    return@withContext Result.failure(IOException(message))
                }
                val body = response.body.string()
                val json = Json { ignoreUnknownKeys = true }
                val obj = json.parseToJsonElement(body).jsonObject
                val count = obj["character_count"]?.jsonPrimitive?.longOrNull ?: 0L
                val limit = obj["character_limit"]?.jsonPrimitive?.longOrNull ?: 500_000L
                Result.success(DeepLUsage(characterCount = count, characterLimit = limit))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun validateGemini(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API-ключ не указан"))
        }
        try {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey.trim()}")
                .get()
                .build()
            client.newCall(request).await().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val message = when (response.code) {
                        400, 403 -> "Неверный API-ключ Gemini"
                        429 -> "Превышен лимит запросов (429)"
                        else -> "HTTP ${response.code}: ${response.message}"
                    }
                    Result.failure(IOException(message))
                }
            }
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
            val bodyJson = """{"model":"$model","messages":[{"role":"user","content":"Hi"}],"max_tokens":1}"""
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
                    val message = when (response.code) {
                        401, 403 -> "Неверный API-ключ"
                        404 -> "Модель \"$model\" не найдена"
                        429 -> "Лимит исчерпан или модель перегружена (429)"
                        else -> "HTTP ${response.code}: ${response.message}"
                    }
                    Result.failure(IOException(message))
                }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

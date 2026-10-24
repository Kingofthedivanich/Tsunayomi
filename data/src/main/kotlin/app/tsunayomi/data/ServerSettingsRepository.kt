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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
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
        if (config.apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API key is not set"))
        }
        try {
            val request = Request.Builder()
                .url("${config.endpoint.trimEnd('/')}/usage")
                .header("Authorization", "DeepL-Auth-Key ${config.apiKey.trim()}")
                .get()
                .build()

            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
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
}

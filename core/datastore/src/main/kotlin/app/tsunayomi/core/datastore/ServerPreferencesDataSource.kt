package app.tsunayomi.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.tsunayomi.core.model.GeminiConfig
import app.tsunayomi.core.model.KomgaConfig
import app.tsunayomi.core.model.OpenAiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Self-hosted server connections. Secrets are stored encrypted, never in plain text. */
@Singleton
class ServerPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    val komga: Flow<KomgaConfig?> = dataStore.data.map { prefs ->
        val url = prefs[Keys.komgaUrl] ?: return@map null
        KomgaConfig(
            url = url,
            username = prefs[Keys.komgaUsername].orEmpty(),
            password = prefs[Keys.komgaPassword]?.let(cipher::decrypt).orEmpty(),
            apiKey = prefs[Keys.komgaApiKey]?.let(cipher::decrypt).orEmpty(),
        )
    }.distinctUntilChanged()

    suspend fun setKomga(config: KomgaConfig?) {
        dataStore.edit { prefs ->
            if (config == null) {
                listOf(Keys.komgaUrl, Keys.komgaUsername, Keys.komgaPassword, Keys.komgaApiKey).forEach(prefs::remove)
                return@edit
            }
            prefs[Keys.komgaUrl] = config.url
            prefs[Keys.komgaUsername] = config.username
            prefs.putSecret(Keys.komgaPassword, config.password)
            prefs.putSecret(Keys.komgaApiKey, config.apiKey)
        }
    }

    val gemini: Flow<GeminiConfig> = dataStore.data.map { prefs ->
        GeminiConfig(
            apiKey = prefs[Keys.geminiApiKey]?.let(cipher::decrypt).orEmpty(),
            models = prefs[Keys.geminiModels]
                ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.ifEmpty { null }
                ?: GeminiConfig.DEFAULT_MODELS,
        )
    }.distinctUntilChanged()

    suspend fun setGemini(config: GeminiConfig) {
        dataStore.edit { prefs ->
            prefs.putSecret(Keys.geminiApiKey, config.apiKey.trim())
            prefs[Keys.geminiModels] = config.models.joinToString(",") { it.trim() }
        }
    }

    val openAi: Flow<OpenAiConfig> = dataStore.data.map { prefs ->
        OpenAiConfig(
            baseUrl = prefs[Keys.openAiBaseUrl]?.trim()?.ifEmpty { null } ?: OpenAiConfig.DEFAULT_BASE_URL,
            apiKey = prefs[Keys.openAiApiKey]?.let(cipher::decrypt).orEmpty(),
            model = prefs[Keys.openAiModel]?.trim()?.ifEmpty { null } ?: OpenAiConfig.DEFAULT_MODEL,
        )
    }.distinctUntilChanged()

    suspend fun setOpenAi(config: OpenAiConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.openAiBaseUrl] = config.baseUrl.trim()
            prefs.putSecret(Keys.openAiApiKey, config.apiKey.trim())
            prefs[Keys.openAiModel] = config.model.trim()
        }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.putSecret(
        key: Preferences.Key<String>,
        value: String,
    ) {
        if (value.isEmpty()) remove(key) else this[key] = cipher.encrypt(value)
    }

    private object Keys {
        val komgaUrl = stringPreferencesKey("komga_url")
        val komgaUsername = stringPreferencesKey("komga_username")
        val komgaPassword = stringPreferencesKey("komga_password")
        val komgaApiKey = stringPreferencesKey("komga_api_key")
        val geminiApiKey = stringPreferencesKey("gemini_api_key")
        val geminiModels = stringPreferencesKey("gemini_models")
        val openAiBaseUrl = stringPreferencesKey("openai_base_url")
        val openAiApiKey = stringPreferencesKey("openai_api_key")
        val openAiModel = stringPreferencesKey("openai_model")
    }
}

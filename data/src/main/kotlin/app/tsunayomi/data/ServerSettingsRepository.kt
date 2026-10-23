package app.tsunayomi.data

import app.tsunayomi.core.datastore.ServerPreferencesDataSource
import app.tsunayomi.core.model.GeminiConfig
import app.tsunayomi.core.model.KomgaConfig
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerSettingsRepository @Inject constructor(
    private val preferences: ServerPreferencesDataSource,
) {
    val komga: Flow<KomgaConfig?> = preferences.komga

    suspend fun setKomga(config: KomgaConfig?) = preferences.setKomga(config)

    val gemini: Flow<GeminiConfig> = preferences.gemini

    suspend fun setGemini(config: GeminiConfig) = preferences.setGemini(config)
}

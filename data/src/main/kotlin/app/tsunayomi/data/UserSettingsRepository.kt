package app.tsunayomi.data

import app.tsunayomi.core.datastore.UserPreferencesDataSource
import app.tsunayomi.core.model.ThemeMode
import app.tsunayomi.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSettingsRepository @Inject constructor(
    private val preferences: UserPreferencesDataSource,
) {
    val settings: Flow<UserSettings> = preferences.settings

    suspend fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode)

    suspend fun setDynamicColor(enabled: Boolean) = preferences.setDynamicColor(enabled)

    suspend fun setPureBlack(enabled: Boolean) = preferences.setPureBlack(enabled)
}

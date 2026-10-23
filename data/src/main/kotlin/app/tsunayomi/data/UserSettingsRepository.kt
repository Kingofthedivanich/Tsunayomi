package app.tsunayomi.data

import app.tsunayomi.core.datastore.UserPreferencesDataSource
import app.tsunayomi.core.model.LibrarySort
import app.tsunayomi.core.model.ReaderMode
import app.tsunayomi.core.model.ThemeMode
import app.tsunayomi.core.model.TranslationEngine
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

    suspend fun setLocalLibraryUri(uri: String?) = preferences.setLocalLibraryUri(uri)

    suspend fun setDefaultReaderMode(mode: ReaderMode) = preferences.setDefaultReaderMode(mode)

    suspend fun setVolumeKeysNavigation(enabled: Boolean) = preferences.setVolumeKeysNavigation(enabled)

    suspend fun setTranslationEngine(engine: TranslationEngine) = preferences.setTranslationEngine(engine)

    suspend fun setTranslationTargetLang(lang: String) = preferences.setTranslationTargetLang(lang)

    suspend fun setTranslateByDefault(enabled: Boolean) = preferences.setTranslateByDefault(enabled)

    suspend fun setLibrarySort(sort: LibrarySort, ascending: Boolean) = preferences.setLibrarySort(sort, ascending)
}

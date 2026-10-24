package app.tsunayomi.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.tsunayomi.core.model.LibrarySort
import app.tsunayomi.core.model.ReaderMode
import app.tsunayomi.core.model.ThemeMode
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        val defaults = UserSettings()
        UserSettings(
            themeMode = prefs[Keys.themeMode].toEnum(defaults.themeMode),
            dynamicColor = prefs[Keys.dynamicColor] ?: defaults.dynamicColor,
            pureBlack = prefs[Keys.pureBlack] ?: defaults.pureBlack,
            localLibraryUri = prefs[Keys.localLibraryUri],
            defaultReaderMode = prefs[Keys.defaultReaderMode].toEnum(defaults.defaultReaderMode),
            volumeKeysNavigation = prefs[Keys.volumeKeysNavigation] ?: defaults.volumeKeysNavigation,
            librarySort = prefs[Keys.librarySort].toEnum(defaults.librarySort),
            librarySortAscending = prefs[Keys.librarySortAscending] ?: defaults.librarySortAscending,
            translationEngine = prefs[Keys.translationEngine].toEnum(defaults.translationEngine),
            translationTargetLang = prefs[Keys.translationTargetLang] ?: defaults.translationTargetLang,
            translateByDefault = prefs[Keys.translateByDefault] ?: defaults.translateByDefault,
            translateOnDownload = prefs[Keys.translateOnDownload] ?: defaults.translateOnDownload,
            libraryUpdateIntervalHours = prefs[Keys.libraryUpdateInterval] ?: defaults.libraryUpdateIntervalHours,
            libraryUpdateWifiOnly = prefs[Keys.libraryUpdateWifiOnly] ?: defaults.libraryUpdateWifiOnly,
            autoDownloadNewChapters = prefs[Keys.autoDownloadNew] ?: defaults.autoDownloadNewChapters,
            incognitoMode = prefs[Keys.incognitoMode] ?: defaults.incognitoMode,
            appLockEnabled = prefs[Keys.appLockEnabled] ?: defaults.appLockEnabled,
            appLockTimeoutMinutes = prefs[Keys.appLockTimeoutMinutes] ?: defaults.appLockTimeoutMinutes,
            secureScreen = prefs[Keys.secureScreen] ?: defaults.secureScreen,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.dynamicColor] = enabled }
    }

    suspend fun setPureBlack(enabled: Boolean) {
        dataStore.edit { it[Keys.pureBlack] = enabled }
    }

    suspend fun setLocalLibraryUri(uri: String?) {
        dataStore.edit {
            if (uri == null) it.remove(Keys.localLibraryUri) else it[Keys.localLibraryUri] = uri
        }
    }

    suspend fun setDefaultReaderMode(mode: ReaderMode) {
        dataStore.edit { it[Keys.defaultReaderMode] = mode.name }
    }

    suspend fun setVolumeKeysNavigation(enabled: Boolean) {
        dataStore.edit { it[Keys.volumeKeysNavigation] = enabled }
    }

    suspend fun setLibrarySort(sort: LibrarySort, ascending: Boolean) {
        dataStore.edit {
            it[Keys.librarySort] = sort.name
            it[Keys.librarySortAscending] = ascending
        }
    }

    suspend fun setTranslationEngine(engine: TranslationEngine) {
        dataStore.edit { it[Keys.translationEngine] = engine.name }
    }

    suspend fun setTranslationTargetLang(lang: String) {
        dataStore.edit { it[Keys.translationTargetLang] = lang }
    }

    suspend fun setLibraryUpdateInterval(hours: Int) {
        dataStore.edit { it[Keys.libraryUpdateInterval] = hours }
    }

    suspend fun setLibraryUpdateWifiOnly(enabled: Boolean) {
        dataStore.edit { it[Keys.libraryUpdateWifiOnly] = enabled }
    }

    suspend fun setAutoDownloadNewChapters(enabled: Boolean) {
        dataStore.edit { it[Keys.autoDownloadNew] = enabled }
    }

    suspend fun setTranslateOnDownload(enabled: Boolean) {
        dataStore.edit { it[Keys.translateOnDownload] = enabled }
    }

    suspend fun setTranslateByDefault(enabled: Boolean) {
        dataStore.edit { it[Keys.translateByDefault] = enabled }
    }

    suspend fun setIncognitoMode(enabled: Boolean) {
        dataStore.edit { it[Keys.incognitoMode] = enabled }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.appLockEnabled] = enabled }
    }

    suspend fun setAppLockTimeoutMinutes(minutes: Int) {
        dataStore.edit { it[Keys.appLockTimeoutMinutes] = minutes }
    }

    suspend fun setSecureScreen(enabled: Boolean) {
        dataStore.edit { it[Keys.secureScreen] = enabled }
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val pureBlack = booleanPreferencesKey("pure_black")
        val localLibraryUri = stringPreferencesKey("local_library_uri")
        val defaultReaderMode = stringPreferencesKey("default_reader_mode")
        val volumeKeysNavigation = booleanPreferencesKey("volume_keys_navigation")
        val librarySort = stringPreferencesKey("library_sort")
        val librarySortAscending = booleanPreferencesKey("library_sort_ascending")
        val translationEngine = stringPreferencesKey("translation_engine")
        val translationTargetLang = stringPreferencesKey("translation_target_lang")
        val translateByDefault = booleanPreferencesKey("translate_by_default")
        val translateOnDownload = booleanPreferencesKey("translate_on_download")
        val libraryUpdateInterval = intPreferencesKey("library_update_interval_hours")
        val libraryUpdateWifiOnly = booleanPreferencesKey("library_update_wifi_only")
        val autoDownloadNew = booleanPreferencesKey("auto_download_new_chapters")
        val incognitoMode = booleanPreferencesKey("incognito_mode")
        val appLockEnabled = booleanPreferencesKey("app_lock_enabled")
        val appLockTimeoutMinutes = intPreferencesKey("app_lock_timeout_minutes")
        val secureScreen = booleanPreferencesKey("secure_screen")
    }
}

package app.tsunayomi.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.tsunayomi.core.model.LibrarySort
import app.tsunayomi.core.model.ReaderMode
import app.tsunayomi.core.model.ThemeMode
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
    }
}

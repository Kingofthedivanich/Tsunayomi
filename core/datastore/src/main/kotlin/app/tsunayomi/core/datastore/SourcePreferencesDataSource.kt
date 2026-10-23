package app.tsunayomi.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Per-source choices that are not credentials, e.g. which chapter languages MangaDex should list. */
@Singleton
class SourcePreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** Chapter languages for MangaDex (ISO codes as MangaDex spells them); null until the user picks some. */
    val mangaDexLanguages: Flow<Set<String>?> = dataStore.data
        .map { it[Keys.mangaDexLanguages]?.takeIf { set -> set.isNotEmpty() } }
        .distinctUntilChanged()

    suspend fun setMangaDexLanguages(languages: Set<String>) {
        dataStore.edit { it[Keys.mangaDexLanguages] = languages }
    }

    private object Keys {
        val mangaDexLanguages = stringSetPreferencesKey("mangadex_languages")
    }
}

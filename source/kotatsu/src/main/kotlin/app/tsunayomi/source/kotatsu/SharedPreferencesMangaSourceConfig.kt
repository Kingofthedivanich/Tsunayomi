package app.tsunayomi.source.kotatsu

import android.content.SharedPreferences
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig

internal class SharedPreferencesMangaSourceConfig(
    private val prefs: SharedPreferences,
) : MangaSourceConfig {

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: ConfigKey<T>): T {
        if (!prefs.contains(key.key)) {
            return key.defaultValue
        }
        return when (val def = key.defaultValue) {
            is Boolean -> prefs.getBoolean(key.key, def) as T
            is Int -> prefs.getInt(key.key, def) as T
            is Long -> prefs.getLong(key.key, def) as T
            is Float -> prefs.getFloat(key.key, def) as T
            is String -> (prefs.getString(key.key, def) ?: def) as T
            is Set<*> -> (prefs.getStringSet(key.key, def as Set<String>) ?: def) as T
            else -> def
        }
    }
}

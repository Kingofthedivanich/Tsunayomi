package app.tsunayomi.feature.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** In-app language. Stored by AppCompat (per-app locale), not in our DataStore. */
internal enum class AppLanguage(val tag: String, @param:StringRes val label: Int) {
    System("", R.string.feature_settings_language_system),
    English("en", R.string.feature_settings_language_english),
    Russian("ru", R.string.feature_settings_language_russian),
    ;

    fun apply() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    companion object {
        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales()[0]?.language ?: return System
            return entries.firstOrNull { it.tag == tag } ?: System
        }
    }
}

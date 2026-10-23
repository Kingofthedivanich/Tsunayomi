package app.tsunayomi.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tsunayomi.core.model.GeminiConfig
import app.tsunayomi.core.model.ReaderMode
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.core.model.ThemeMode
import app.tsunayomi.core.model.UserSettings
import app.tsunayomi.data.ServerSettingsRepository
import app.tsunayomi.data.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val repository: UserSettingsRepository,
    private val serverSettings: ServerSettingsRepository,
) : ViewModel() {
    val gemini: StateFlow<GeminiConfig?> = serverSettings.gemini
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun setTranslationEngine(engine: TranslationEngine) {
        viewModelScope.launch { repository.setTranslationEngine(engine) }
    }

    fun setTranslationTargetLang(lang: String) {
        viewModelScope.launch { repository.setTranslationTargetLang(lang) }
    }

    fun setLibraryUpdateInterval(hours: Int) {
        viewModelScope.launch { repository.setLibraryUpdateInterval(hours) }
    }

    fun setLibraryUpdateWifiOnly(enabled: Boolean) {
        viewModelScope.launch { repository.setLibraryUpdateWifiOnly(enabled) }
    }

    fun setAutoDownloadNewChapters(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoDownloadNewChapters(enabled) }
    }

    fun setTranslateOnDownload(enabled: Boolean) {
        viewModelScope.launch { repository.setTranslateOnDownload(enabled) }
    }

    fun setTranslateByDefault(enabled: Boolean) {
        viewModelScope.launch { repository.setTranslateByDefault(enabled) }
    }

    fun setGemini(config: GeminiConfig) {
        viewModelScope.launch { serverSettings.setGemini(config) }
    }

    val komgaUrl: StateFlow<String?> = serverSettings.komga.map { it?.url }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    val settings: StateFlow<UserSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    fun setPureBlack(enabled: Boolean) {
        viewModelScope.launch { repository.setPureBlack(enabled) }
    }

    fun setLocalLibraryUri(uri: String) {
        viewModelScope.launch { repository.setLocalLibraryUri(uri) }
    }

    fun setDefaultReaderMode(mode: ReaderMode) {
        viewModelScope.launch { repository.setDefaultReaderMode(mode) }
    }

    fun setVolumeKeysNavigation(enabled: Boolean) {
        viewModelScope.launch { repository.setVolumeKeysNavigation(enabled) }
    }
}

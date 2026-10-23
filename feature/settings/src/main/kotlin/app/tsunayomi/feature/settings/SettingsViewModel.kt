package app.tsunayomi.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tsunayomi.core.model.ReaderMode
import app.tsunayomi.core.model.ThemeMode
import app.tsunayomi.core.model.UserSettings
import app.tsunayomi.data.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val repository: UserSettingsRepository,
) : ViewModel() {
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

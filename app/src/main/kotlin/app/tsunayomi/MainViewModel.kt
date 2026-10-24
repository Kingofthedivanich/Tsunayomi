package app.tsunayomi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tsunayomi.core.model.UserSettings
import app.tsunayomi.data.LibraryRepository
import app.tsunayomi.data.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: UserSettingsRepository,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {
    init {
        viewModelScope.launch {
            libraryRepository.ensureDefaultCategories()
        }
    }

    /** Null until the first read from disk, so the UI is not drawn with the wrong theme. */
    val settings: StateFlow<UserSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.setOnboardingCompleted(true)
        }
    }
}

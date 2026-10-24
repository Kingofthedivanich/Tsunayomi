package app.tsunayomi.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tsunayomi.core.model.BackupResult
import app.tsunayomi.core.model.RestoreProgress
import app.tsunayomi.core.model.RestoreResult
import app.tsunayomi.data.BackupRepository
import app.tsunayomi.data.RestoreStep
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Creating : BackupUiState
    data class Restoring(val progress: RestoreProgress) : BackupUiState
    data class Success(val messageRes: Int, val mangaCount: Int, val categoryCount: Int, val chapterCount: Int) : BackupUiState
    data class Error(val message: String) : BackupUiState
}

@HiltViewModel
internal class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun resetState() {
        _state.value = BackupUiState.Idle
    }

    fun defaultBackupFilename(): String {
        val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        return "tsunayomi_$date.tsunabackup"
    }

    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            _state.value = BackupUiState.Creating
            val result = try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    backupRepository.createBackup(output, appVersion = "1.0.0")
                } ?: BackupResult.Error(IllegalStateException("Cannot open output stream"))
            } catch (t: Throwable) {
                BackupResult.Error(t)
            }

            _state.value = when (result) {
                is BackupResult.Success -> BackupUiState.Success(
                    messageRes = R.string.feature_settings_backup_res_created,
                    mangaCount = result.mangaCount,
                    categoryCount = result.categoryCount,
                    chapterCount = result.chapterCount,
                )
                is BackupResult.Error -> BackupUiState.Error(
                    result.throwable.localizedMessage ?: "Unknown error"
                )
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            _state.value = BackupUiState.Restoring(RestoreProgress(0, 0, ""))
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open input stream")
                input.use { stream ->
                    backupRepository.restoreBackup(stream).collect { step ->
                        when (step) {
                            is RestoreStep.Progress -> {
                                _state.value = BackupUiState.Restoring(
                                    RestoreProgress(step.current, step.total, step.currentTitle),
                                )
                            }
                            is RestoreStep.Finished -> {
                                _state.value = when (val res = step.result) {
                                    is RestoreResult.Success -> BackupUiState.Success(
                                        messageRes = R.string.feature_settings_backup_res_restored,
                                        mangaCount = res.mangaCount,
                                        categoryCount = res.categoryCount,
                                        chapterCount = res.chapterCount,
                                    )
                                    is RestoreResult.Error -> BackupUiState.Error(
                                        res.throwable.localizedMessage ?: "Unknown error"
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                _state.value = BackupUiState.Error(t.localizedMessage ?: "Unknown error")
            }
        }
    }
}

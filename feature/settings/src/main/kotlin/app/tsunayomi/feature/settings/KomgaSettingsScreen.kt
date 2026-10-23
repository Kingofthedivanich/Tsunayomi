package app.tsunayomi.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.tsunayomi.core.model.KomgaConfig
import app.tsunayomi.data.ServerSettingsRepository
import app.tsunayomi.data.SourceManager
import app.tsunayomi.source.api.SourceIds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data object KomgaSettingsRoute

fun NavGraphBuilder.komgaSettingsScreen(onBack: () -> Unit) {
    composable<KomgaSettingsRoute> { KomgaSettingsScreen(onBack) }
}

fun NavController.navigateToKomgaSettings() = navigate(KomgaSettingsRoute)

internal sealed interface ConnectionCheck {
    data object Idle : ConnectionCheck
    data object Running : ConnectionCheck
    data object Success : ConnectionCheck
    data class Failure(val message: String) : ConnectionCheck
}

internal data class KomgaSettingsUiState(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
    val check: ConnectionCheck = ConnectionCheck.Idle,
    val loaded: Boolean = false,
)

@HiltViewModel
internal class KomgaSettingsViewModel @Inject constructor(
    private val repository: ServerSettingsRepository,
    private val sourceManager: SourceManager,
) : ViewModel() {
    private val _state = MutableStateFlow(KomgaSettingsUiState())
    val state: StateFlow<KomgaSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val config = repository.komga.first()
            _state.value = KomgaSettingsUiState(
                url = config?.url.orEmpty(),
                username = config?.username.orEmpty(),
                password = config?.password.orEmpty(),
                apiKey = config?.apiKey.orEmpty(),
                loaded = true,
            )
        }
    }

    fun update(transform: KomgaSettingsUiState.() -> KomgaSettingsUiState) =
        _state.update { it.transform().copy(check = ConnectionCheck.Idle) }

    /** Saves the settings, then proves them by listing the server's first page of series. */
    fun saveAndCheck() {
        val current = _state.value
        val url = normalizeUrl(current.url) ?: return
        _state.update { it.copy(url = url, check = ConnectionCheck.Running) }
        viewModelScope.launch {
            repository.setKomga(KomgaConfig(url, current.username.trim(), current.password, current.apiKey.trim()))
            val check = try {
                sourceManager.getCatalog(SourceIds.KOMGA).getPopular(1)
                ConnectionCheck.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ConnectionCheck.Failure(e.message ?: e.toString())
            }
            _state.update { it.copy(check = check) }
        }
    }

    fun remove() {
        viewModelScope.launch {
            repository.setKomga(null)
            _state.value = KomgaSettingsUiState(loaded = true)
        }
    }

    private fun normalizeUrl(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KomgaSettingsScreen(onBack: () -> Unit, viewModel: KomgaSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_settings_komga_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.feature_settings_back))
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.feature_settings_komga_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.url,
                onValueChange = { value -> viewModel.update { copy(url = value) } },
                label = { Text(stringResource(R.string.feature_settings_komga_url)) },
                placeholder = { Text("http://192.168.1.10:25600") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = { value -> viewModel.update { copy(username = value) } },
                label = { Text(stringResource(R.string.feature_settings_komga_username)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = { value -> viewModel.update { copy(password = value) } },
                label = { Text(stringResource(R.string.feature_settings_komga_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = { value -> viewModel.update { copy(apiKey = value) } },
                label = { Text(stringResource(R.string.feature_settings_komga_api_key)) },
                supportingText = { Text(stringResource(R.string.feature_settings_komga_api_key_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = viewModel::saveAndCheck,
                    enabled = state.url.isNotBlank() && state.check != ConnectionCheck.Running,
                ) { Text(stringResource(R.string.feature_settings_komga_save)) }
                OutlinedButton(onClick = viewModel::remove) { Text(stringResource(R.string.feature_settings_komga_remove)) }
            }
            when (val check = state.check) {
                ConnectionCheck.Idle -> Unit
                ConnectionCheck.Running -> CircularProgressIndicator()
                ConnectionCheck.Success -> Text(
                    stringResource(R.string.feature_settings_komga_connected),
                    color = MaterialTheme.colorScheme.primary,
                )
                is ConnectionCheck.Failure -> Text(
                    stringResource(R.string.feature_settings_komga_failed, check.message),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

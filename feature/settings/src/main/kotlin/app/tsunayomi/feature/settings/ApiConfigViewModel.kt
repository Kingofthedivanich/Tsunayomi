package app.tsunayomi.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tsunayomi.core.model.DeepLConfig
import app.tsunayomi.core.model.GeminiConfig
import app.tsunayomi.core.model.OpenAiConfig
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.data.ServerSettingsRepository
import app.tsunayomi.data.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface VerificationState {
    data object Idle : VerificationState
    data object Loading : VerificationState
    data class Success(val message: String? = null) : VerificationState
    data class Error(val message: String, val alternativeModels: List<String> = emptyList()) : VerificationState
}

@HiltViewModel
class ApiConfigViewModel @Inject constructor(
    private val serverSettings: ServerSettingsRepository,
    private val userSettings: UserSettingsRepository,
) : ViewModel() {

    private val _selectedEngine = MutableStateFlow(TranslationEngine.Gemini)
    val selectedEngine: StateFlow<TranslationEngine> = _selectedEngine.asStateFlow()

    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _geminiStatus = MutableStateFlow<VerificationState>(VerificationState.Idle)
    val geminiStatus: StateFlow<VerificationState> = _geminiStatus.asStateFlow()

    private val _openAiBaseUrl = MutableStateFlow(OpenAiConfig.PRESET_OPENROUTER.baseUrl)
    val openAiBaseUrl: StateFlow<String> = _openAiBaseUrl.asStateFlow()

    private val _openAiApiKey = MutableStateFlow("")
    val openAiApiKey: StateFlow<String> = _openAiApiKey.asStateFlow()

    private val _openAiModel = MutableStateFlow(OpenAiConfig.PRESET_OPENROUTER.model)
    val openAiModel: StateFlow<String> = _openAiModel.asStateFlow()

    private val _openRouterModels = MutableStateFlow(OpenAiConfig.DEFAULT_OPENROUTER_FREE_MODELS)
    val openRouterModels: StateFlow<List<String>> = _openRouterModels.asStateFlow()

    private val _openAiStatus = MutableStateFlow<VerificationState>(VerificationState.Idle)
    val openAiStatus: StateFlow<VerificationState> = _openAiStatus.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    private val _deepLApiKey = MutableStateFlow("")
    val deepLApiKey: StateFlow<String> = _deepLApiKey.asStateFlow()

    private val _deepLStatus = MutableStateFlow<VerificationState>(VerificationState.Idle)
    val deepLStatus: StateFlow<VerificationState> = _deepLStatus.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = userSettings.settings.first()
            _selectedEngine.value = if (settings.translationEngine == TranslationEngine.MlKit) {
                TranslationEngine.Gemini
            } else {
                settings.translationEngine
            }

            val gemini = serverSettings.gemini.first()
            _geminiApiKey.value = gemini.apiKey

            val openAi = serverSettings.openAi.first()
            if (openAi.baseUrl.isNotBlank()) _openAiBaseUrl.value = openAi.baseUrl
            _openAiApiKey.value = openAi.apiKey
            if (openAi.model.isNotBlank()) _openAiModel.value = openAi.model

            val deepL = serverSettings.deepL.first()
            _deepLApiKey.value = deepL.apiKey

            // Fetch dynamic free models in background
            refreshOpenRouterModels()
        }
    }

    fun selectEngine(engine: TranslationEngine) {
        _selectedEngine.value = engine
    }

    fun setGeminiKey(key: String) {
        _geminiApiKey.value = key
        _geminiStatus.value = VerificationState.Idle
    }

    fun verifyGeminiKey() {
        val key = _geminiApiKey.value.trim()
        if (key.isBlank()) {
            _geminiStatus.value = VerificationState.Error("Ключ не может быть пустым")
            return
        }
        viewModelScope.launch {
            _geminiStatus.value = VerificationState.Loading
            val result = serverSettings.validateGemini(key)
            _geminiStatus.value = result.fold(
                onSuccess = { VerificationState.Success() },
                onFailure = { VerificationState.Error(it.message ?: "Ошибка проверки ключа") },
            )
        }
    }

    fun setOpenAiBaseUrl(url: String) {
        _openAiBaseUrl.value = url
        _openAiStatus.value = VerificationState.Idle
    }

    fun setOpenAiKey(key: String) {
        _openAiApiKey.value = key
        _openAiStatus.value = VerificationState.Idle
    }

    fun setOpenAiModel(model: String) {
        _openAiModel.value = model
        _openAiStatus.value = VerificationState.Idle
    }

    fun refreshOpenRouterModels() {
        viewModelScope.launch {
            _isFetchingModels.value = true
            val result = serverSettings.fetchOpenRouterFreeModels()
            result.onSuccess { models ->
                _openRouterModels.value = models
            }
            _isFetchingModels.value = false
        }
    }

    fun verifyOpenAiModel() {
        val url = _openAiBaseUrl.value.trim()
        val key = _openAiApiKey.value.trim()
        val model = _openAiModel.value.trim()
        if (url.isBlank() || model.isBlank()) {
            _openAiStatus.value = VerificationState.Error("Укажите адрес сервера и модель")
            return
        }
        viewModelScope.launch {
            _openAiStatus.value = VerificationState.Loading
            val result = serverSettings.testOpenAiCompatibleModel(url, key, model)
            _openAiStatus.value = result.fold(
                onSuccess = { VerificationState.Success() },
                onFailure = { error ->
                    val alternatives = _openRouterModels.value.filter { it != model }.take(4)
                    VerificationState.Error(error.message ?: "Модель недоступна", alternatives)
                },
            )
        }
    }

    fun setDeepLKey(key: String) {
        _deepLApiKey.value = key
        _deepLStatus.value = VerificationState.Idle
    }

    fun verifyDeepLKey() {
        val key = _deepLApiKey.value.trim()
        if (key.isBlank()) {
            _deepLStatus.value = VerificationState.Error("Ключ не может быть пустым")
            return
        }
        viewModelScope.launch {
            _deepLStatus.value = VerificationState.Loading
            val result = serverSettings.validateDeepL(key)
            _deepLStatus.value = result.fold(
                onSuccess = { usage ->
                    val msg = "${usage.characterCount} / ${usage.characterLimit} символов (${usage.percentage}%)"
                    VerificationState.Success(msg)
                },
                onFailure = { VerificationState.Error(it.message ?: "Ошибка проверки ключа") },
            )
        }
    }

    fun saveConfig(onCompleted: () -> Unit) {
        viewModelScope.launch {
            val engine = _selectedEngine.value

            val geminiKey = _geminiApiKey.value.trim()
            val currentGemini = serverSettings.gemini.first()
            if (geminiKey != currentGemini.apiKey) {
                serverSettings.setGemini(currentGemini.copy(apiKey = geminiKey))
            }

            val openAiUrl = _openAiBaseUrl.value.trim()
            val openAiKey = _openAiApiKey.value.trim()
            val openAiModel = _openAiModel.value.trim()
            serverSettings.setOpenAi(
                OpenAiConfig(
                    baseUrl = openAiUrl.ifBlank { OpenAiConfig.PRESET_OPENROUTER.baseUrl },
                    apiKey = openAiKey,
                    model = openAiModel.ifBlank { OpenAiConfig.PRESET_OPENROUTER.model },
                ),
            )

            val deepLKey = _deepLApiKey.value.trim()
            serverSettings.setDeepL(DeepLConfig(apiKey = deepLKey))

            userSettings.setTranslationEngine(engine)
            onCompleted()
        }
    }
}

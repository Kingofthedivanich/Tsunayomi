package app.tsunayomi.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.tsunayomi.core.model.DeepLConfig
import app.tsunayomi.core.model.DeepLUsage
import app.tsunayomi.core.model.GeminiConfig
import app.tsunayomi.core.model.OpenAiConfig
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.core.model.UserSettings
import java.util.Locale

/** Target languages offered in settings; "" follows the app language. */
internal val TRANSLATION_TARGETS = listOf(
    "", "ru", "en", "uk", "es", "pt", "de", "fr", "it", "pl", "tr", "id", "vi", "th", "ja", "ko", "zh",
)

@Composable
internal fun languageLabel(tag: String): String {
    if (tag.isEmpty()) return stringResource(R.string.feature_settings_translation_lang_app)
    // Each language is named in itself ("Русский", "日本語"), so the list reads well whatever the UI language.
    val locale = Locale.forLanguageTag(tag)
    val uiLocale = LocalConfiguration.current.locales[0]
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(uiLocale) }
}

@Composable
internal fun TranslationEngine.label(): String = when (this) {
    TranslationEngine.MlKit -> stringResource(R.string.feature_settings_translation_engine_mlkit)
    TranslationEngine.Gemini -> stringResource(R.string.feature_settings_translation_engine_gemini)
    TranslationEngine.OpenAiCompatible -> stringResource(R.string.feature_settings_translation_engine_openai)
    TranslationEngine.DeepL -> stringResource(R.string.feature_settings_translation_engine_deepl)
    else -> name
}

/** The "Translation" section of the settings screen. */
@Composable
internal fun TranslationSection(
    settings: UserSettings,
    gemini: GeminiConfig?,
    openAi: OpenAiConfig?,
    deepL: DeepLConfig?,
    deepLUsage: DeepLUsage?,
    isCheckingDeepLUsage: Boolean,
    onPickEngine: () -> Unit,
    onPickLanguage: () -> Unit,
    onTranslateByDefaultChange: (Boolean) -> Unit,
    onTranslateOnDownloadChange: (Boolean) -> Unit,
    onGeminiChange: (GeminiConfig) -> Unit,
    onOpenAiChange: (OpenAiConfig) -> Unit,
    onDeepLChange: (DeepLConfig) -> Unit,
    onRefreshDeepLUsage: () -> Unit,
    switchItem: @Composable (title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) -> Unit,
) {
    var keyDialog by rememberSaveable { mutableStateOf(false) }
    var modelDialog by rememberSaveable { mutableStateOf(false) }

    var openAiPresetDialog by rememberSaveable { mutableStateOf(false) }
    var openAiUrlDialog by rememberSaveable { mutableStateOf(false) }
    var openAiKeyDialog by rememberSaveable { mutableStateOf(false) }
    var openAiModelDialog by rememberSaveable { mutableStateOf(false) }

    var deepLKeyDialog by rememberSaveable { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.feature_settings_translation_engine)) },
        supportingContent = { Text(settings.translationEngine.label()) },
        modifier = Modifier.clickable(onClick = onPickEngine),
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.feature_settings_translation_lang)) },
        supportingContent = { Text(languageLabel(settings.translationTargetLang)) },
        modifier = Modifier.clickable(onClick = onPickLanguage),
    )
    switchItem(
        stringResource(R.string.feature_settings_translate_by_default),
        stringResource(R.string.feature_settings_translate_by_default_summary),
        settings.translateByDefault,
        onTranslateByDefaultChange,
    )
    switchItem(
        stringResource(R.string.feature_settings_translate_on_download),
        stringResource(R.string.feature_settings_translate_on_download_summary),
        settings.translateOnDownload,
        onTranslateOnDownloadChange,
    )
    if (settings.translationEngine == TranslationEngine.Gemini && gemini != null) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.feature_settings_gemini_key)) },
            supportingContent = {
                Text(
                    if (gemini.apiKey.isBlank()) {
                        stringResource(R.string.feature_settings_gemini_key_missing)
                    } else {
                        stringResource(R.string.feature_settings_gemini_key_set)
                    },
                    color = if (gemini.apiKey.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.clickable { keyDialog = true },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.feature_settings_gemini_model)) },
            supportingContent = { Text(gemini.models.joinToString(" → ")) },
            modifier = Modifier.clickable { modelDialog = true },
        )
    }

    if (settings.translationEngine == TranslationEngine.OpenAiCompatible && openAi != null) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.feature_settings_openai_preset)) },
            supportingContent = { Text(openAi.presetName()) },
            modifier = Modifier.clickable { openAiPresetDialog = true },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.feature_settings_openai_url)) },
            supportingContent = { Text(openAi.baseUrl) },
            modifier = Modifier.clickable { openAiUrlDialog = true },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.feature_settings_openai_key)) },
            supportingContent = {
                Text(
                    if (openAi.apiKey.isBlank()) {
                        stringResource(R.string.feature_settings_openai_key_missing)
                    } else {
                        stringResource(R.string.feature_settings_openai_key_set)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.clickable { openAiKeyDialog = true },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.feature_settings_openai_model)) },
            supportingContent = { Text(openAi.model) },
            modifier = Modifier.clickable { openAiModelDialog = true },
        )
    }

    if (settings.translationEngine == TranslationEngine.DeepL && deepL != null) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.feature_settings_deepl_key)) },
            supportingContent = {
                Text(
                    if (deepL.apiKey.isBlank()) {
                        stringResource(R.string.feature_settings_deepl_key_missing)
                    } else if (deepL.isFreeTier) {
                        stringResource(R.string.feature_settings_deepl_key_set_free)
                    } else {
                        stringResource(R.string.feature_settings_deepl_key_set_pro)
                    },
                    color = if (deepL.apiKey.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.clickable { deepLKeyDialog = true },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.feature_settings_deepl_usage)) },
            supportingContent = {
                Text(
                    when {
                        isCheckingDeepLUsage -> stringResource(R.string.feature_settings_deepl_checking_usage)
                        deepLUsage != null -> stringResource(
                            R.string.feature_settings_deepl_usage_summary,
                            deepLUsage.characterCount,
                            deepLUsage.characterLimit,
                            deepLUsage.percentage,
                        )
                        else -> stringResource(R.string.feature_settings_deepl_check_usage)
                    },
                )
            },
            modifier = Modifier.clickable(onClick = onRefreshDeepLUsage),
        )
    }

    if (deepLKeyDialog && deepL != null) {
        TextValueDialog(
            title = stringResource(R.string.feature_settings_deepl_key),
            hint = stringResource(R.string.feature_settings_deepl_key_hint),
            initial = deepL.apiKey,
            secret = true,
            onConfirm = {
                deepLKeyDialog = false
                onDeepLChange(deepL.copy(apiKey = it))
            },
            onDismiss = { deepLKeyDialog = false },
        )
    }

    if (keyDialog && gemini != null) {
        TextValueDialog(
            title = stringResource(R.string.feature_settings_gemini_key),
            hint = stringResource(R.string.feature_settings_gemini_key_hint),
            initial = gemini.apiKey,
            secret = true,
            onConfirm = {
                keyDialog = false
                onGeminiChange(gemini.copy(apiKey = it))
            },
            onDismiss = { keyDialog = false },
        )
    }
    if (modelDialog && gemini != null) {
        TextValueDialog(
            title = stringResource(R.string.feature_settings_gemini_model),
            hint = stringResource(R.string.feature_settings_gemini_model_hint, GeminiConfig.DEFAULT_MODELS.joinToString(", ")),
            initial = gemini.models.joinToString(", "),
            secret = false,
            onConfirm = {
                modelDialog = false
                val models = it.split(',').map(String::trim).filter(String::isNotEmpty)
                onGeminiChange(gemini.copy(models = models.ifEmpty { GeminiConfig.DEFAULT_MODELS }))
            },
            onDismiss = { modelDialog = false },
        )
    }

    if (openAiPresetDialog && openAi != null) {
        ChoiceDialog(
            title = stringResource(R.string.feature_settings_openai_preset),
            options = OPENAI_PRESETS,
            selected = OPENAI_PRESETS.firstOrNull { it.baseUrl == openAi.baseUrl } ?: OPENAI_PRESETS[0],
            label = { it.name },
            onSelect = { preset ->
                openAiPresetDialog = false
                onOpenAiChange(openAi.copy(baseUrl = preset.baseUrl, model = preset.model))
            },
            onDismiss = { openAiPresetDialog = false },
        )
    }

    if (openAiUrlDialog && openAi != null) {
        TextValueDialog(
            title = stringResource(R.string.feature_settings_openai_url),
            hint = stringResource(R.string.feature_settings_openai_url_hint),
            initial = openAi.baseUrl,
            secret = false,
            onConfirm = {
                openAiUrlDialog = false
                onOpenAiChange(openAi.copy(baseUrl = it))
            },
            onDismiss = { openAiUrlDialog = false },
        )
    }

    if (openAiKeyDialog && openAi != null) {
        TextValueDialog(
            title = stringResource(R.string.feature_settings_openai_key),
            hint = stringResource(R.string.feature_settings_openai_key_hint),
            initial = openAi.apiKey,
            secret = true,
            onConfirm = {
                openAiKeyDialog = false
                onOpenAiChange(openAi.copy(apiKey = it))
            },
            onDismiss = { openAiKeyDialog = false },
        )
    }

    if (openAiModelDialog && openAi != null) {
        TextValueDialog(
            title = stringResource(R.string.feature_settings_openai_model),
            hint = stringResource(R.string.feature_settings_openai_model_hint),
            initial = openAi.model,
            secret = false,
            onConfirm = {
                openAiModelDialog = false
                onOpenAiChange(openAi.copy(model = it))
            },
            onDismiss = { openAiModelDialog = false },
        )
    }
}

private data class OpenAiPreset(val name: String, val baseUrl: String, val model: String)

private val OPENAI_PRESETS = listOf(
    OpenAiPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    OpenAiPreset("OpenRouter", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat"),
    OpenAiPreset("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
    OpenAiPreset("Ollama (Local Wi-Fi)", "http://192.168.1.100:11434/v1", "qwen2.5:7b"),
    OpenAiPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
)

private fun OpenAiConfig.presetName(): String =
    OPENAI_PRESETS.firstOrNull { it.baseUrl == baseUrl && it.model == model }?.name
        ?: OPENAI_PRESETS.firstOrNull { it.baseUrl == baseUrl }?.let { "${it.name} ($model)" }
        ?: "$model ($baseUrl)"

@Composable
private fun TextValueDialog(
    title: String,
    hint: String,
    initial: String,
    secret: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(hint, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()) }) { Text(stringResource(R.string.feature_settings_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_settings_cancel)) } },
    )
}

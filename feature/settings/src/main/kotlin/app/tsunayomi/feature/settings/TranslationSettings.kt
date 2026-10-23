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
import app.tsunayomi.core.model.GeminiConfig
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
    else -> name
}

/** The "Translation" section of the settings screen. */
@Composable
internal fun TranslationSection(
    settings: UserSettings,
    gemini: GeminiConfig?,
    onPickEngine: () -> Unit,
    onPickLanguage: () -> Unit,
    onTranslateByDefaultChange: (Boolean) -> Unit,
    onGeminiChange: (GeminiConfig) -> Unit,
    switchItem: @Composable (title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) -> Unit,
) {
    var keyDialog by rememberSaveable { mutableStateOf(false) }
    var modelDialog by rememberSaveable { mutableStateOf(false) }

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
}

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

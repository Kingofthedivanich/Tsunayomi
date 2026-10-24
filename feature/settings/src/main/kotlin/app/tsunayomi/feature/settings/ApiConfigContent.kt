package app.tsunayomi.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.data.ApiCheckException

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApiConfigContent(
    viewModel: ApiConfigViewModel,
    modifier: Modifier = Modifier,
    onSaveCompleted: () -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    val selectedEngine by viewModel.selectedEngine.collectAsStateWithLifecycle()

    val geminiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val geminiStatus by viewModel.geminiStatus.collectAsStateWithLifecycle()

    val openAiUrl by viewModel.openAiBaseUrl.collectAsStateWithLifecycle()
    val openAiKey by viewModel.openAiApiKey.collectAsStateWithLifecycle()
    val openAiModel by viewModel.openAiModel.collectAsStateWithLifecycle()
    val openRouterModels by viewModel.openRouterModels.collectAsStateWithLifecycle()
    val openAiStatus by viewModel.openAiStatus.collectAsStateWithLifecycle()
    val isFetchingModels by viewModel.isFetchingModels.collectAsStateWithLifecycle()

    val deepLKey by viewModel.deepLApiKey.collectAsStateWithLifecycle()
    val deepLStatus by viewModel.deepLStatus.collectAsStateWithLifecycle()

    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_api_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 1. Google Gemini
        EngineCard(
            title = stringResource(R.string.api_config_engine_gemini),
            description = stringResource(R.string.api_config_engine_gemini_desc),
            selected = selectedEngine == TranslationEngine.Gemini,
            onSelect = { viewModel.selectEngine(TranslationEngine.Gemini) },
        ) {
            ApiKeyInput(
                value = geminiKey,
                onValueChange = viewModel::setGeminiKey,
                onPaste = { clipboardManager.getText()?.text?.let(viewModel::setGeminiKey) },
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = viewModel::verifyGeminiKey,
                    enabled = geminiStatus !is VerificationState.Loading && geminiKey.isNotBlank(),
                ) {
                    if (geminiStatus is VerificationState.Loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.api_config_verify_key))
                }
                Spacer(Modifier.width(12.dp))
                StatusView(status = geminiStatus)
            }
        }

        // 2. OpenRouter (OpenAI-compatible)
        EngineCard(
            title = stringResource(R.string.api_config_engine_openrouter),
            description = stringResource(R.string.api_config_engine_openrouter_desc),
            selected = selectedEngine == TranslationEngine.OpenAiCompatible,
            onSelect = { viewModel.selectEngine(TranslationEngine.OpenAiCompatible) },
        ) {
            ApiKeyInput(
                value = openAiKey,
                onValueChange = viewModel::setOpenAiKey,
                onPaste = { clipboardManager.getText()?.text?.let(viewModel::setOpenAiKey) },
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = openAiModel,
                onValueChange = viewModel::setOpenAiModel,
                label = { Text(stringResource(R.string.api_config_model_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.api_config_suggest_alternative),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = viewModel::refreshOpenRouterModels,
                    enabled = !isFetchingModels,
                ) {
                    if (isFetchingModels) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.api_config_fetch_free_models))
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                openRouterModels.take(4).forEach { modelName ->
                    SuggestionChip(
                        onClick = {
                            viewModel.setOpenAiModel(modelName)
                            viewModel.verifyOpenAiModel()
                        },
                        label = {
                            Text(
                                text = modelName.substringAfterLast('/'),
                                fontWeight = if (modelName == openAiModel) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = viewModel::verifyOpenAiModel,
                    enabled = openAiStatus !is VerificationState.Loading && openAiModel.isNotBlank(),
                ) {
                    if (openAiStatus is VerificationState.Loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.api_config_verify_key))
                }
                Spacer(Modifier.width(12.dp))
                StatusView(status = openAiStatus)
            }

            if (openAiStatus is VerificationState.Error) {
                val error = openAiStatus as VerificationState.Error
                if (error.alternativeModels.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.api_config_suggest_alternative),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        error.alternativeModels.forEach { altModel ->
                            SuggestionChip(
                                onClick = {
                                    viewModel.setOpenAiModel(altModel)
                                    viewModel.verifyOpenAiModel()
                                },
                                label = { Text(altModel.substringAfterLast('/')) },
                            )
                        }
                    }
                }
            }
        }

        // 3. DeepL
        EngineCard(
            title = stringResource(R.string.api_config_engine_deepl),
            description = stringResource(R.string.api_config_engine_deepl_desc),
            selected = selectedEngine == TranslationEngine.DeepL,
            onSelect = { viewModel.selectEngine(TranslationEngine.DeepL) },
        ) {
            ApiKeyInput(
                value = deepLKey,
                onValueChange = viewModel::setDeepLKey,
                onPaste = { clipboardManager.getText()?.text?.let(viewModel::setDeepLKey) },
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = viewModel::verifyDeepLKey,
                    enabled = deepLStatus !is VerificationState.Loading && deepLKey.isNotBlank(),
                ) {
                    if (deepLStatus is VerificationState.Loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.api_config_verify_key))
                }
                Spacer(Modifier.width(12.dp))
                StatusView(status = deepLStatus)
            }
        }

        // 4. Google ML Kit (offline only)
        EngineCard(
            title = stringResource(R.string.api_config_engine_mlkit),
            description = stringResource(R.string.api_config_engine_mlkit_desc),
            selected = selectedEngine == TranslationEngine.MlKit,
            onSelect = { viewModel.selectEngine(TranslationEngine.MlKit) },
            content = null,
        )

        Spacer(Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onSkip != null) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.onboarding_btn_skip))
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Button(
                onClick = { viewModel.saveConfig(onSaveCompleted) },
            ) {
                Text(stringResource(R.string.onboarding_btn_save_continue))
            }
        }
    }
}

@Composable
private fun EngineCard(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    OutlinedCard(
        onClick = onSelect,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected,
                    onClick = onSelect,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected && content != null) {
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun ApiKeyInput(
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.api_config_api_key_label)) },
        placeholder = { Text(stringResource(R.string.api_config_api_key_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            Row {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = onPaste) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = stringResource(R.string.api_config_paste),
                    )
                }
            }
        },
    )
}

@Composable
private fun StatusView(status: VerificationState) {
    when (status) {
        is VerificationState.Success -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = status.usage?.let {
                        stringResource(R.string.api_config_deepl_usage, it.characterCount, it.characterLimit, it.percentage)
                    } ?: stringResource(R.string.api_config_key_valid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        is VerificationState.Error -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = when (status.reason) {
                        ApiCheckException.Reason.NoKey -> stringResource(R.string.api_config_error_no_key)
                        ApiCheckException.Reason.InvalidKey -> stringResource(R.string.api_config_error_invalid_key)
                        ApiCheckException.Reason.QuotaExceeded -> stringResource(R.string.api_config_error_quota)
                        ApiCheckException.Reason.ModelNotFound -> stringResource(R.string.api_config_error_model)
                        ApiCheckException.Reason.Http, null ->
                            stringResource(R.string.api_config_error_other, status.detail.orEmpty())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        else -> {}
    }
}

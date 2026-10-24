package app.tsunayomi.feature.settings

import android.os.Build
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tsunayomi.core.designsystem.component.labelRes
import app.tsunayomi.core.designsystem.component.rememberLibraryFolderPicker
import app.tsunayomi.core.model.GeminiConfig
import app.tsunayomi.core.model.OpenAiConfig
import app.tsunayomi.core.model.ReaderMode
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.core.model.ThemeMode
import app.tsunayomi.core.model.UserSettings

@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onOpenKomga: () -> Unit,
    onOpenBackup: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val komgaUrl by viewModel.komgaUrl.collectAsStateWithLifecycle()
    val gemini by viewModel.gemini.collectAsStateWithLifecycle()
    val openAi by viewModel.openAi.collectAsStateWithLifecycle()
    SettingsContent(
        settings = settings,
        gemini = gemini,
        openAi = openAi,
        onTranslationEngineChange = viewModel::setTranslationEngine,
        onTranslationLangChange = viewModel::setTranslationTargetLang,
        onTranslateByDefaultChange = viewModel::setTranslateByDefault,
        onTranslateOnDownloadChange = viewModel::setTranslateOnDownload,
        onUpdateIntervalChange = viewModel::setLibraryUpdateInterval,
        onUpdateWifiOnlyChange = viewModel::setLibraryUpdateWifiOnly,
        onAutoDownloadNewChange = viewModel::setAutoDownloadNewChapters,
        onGeminiChange = viewModel::setGemini,
        onOpenAiChange = viewModel::setOpenAi,
        komgaUrl = komgaUrl,
        onOpenKomga = onOpenKomga,
        onOpenBackup = onOpenBackup,
        onBack = onBack,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onPureBlackChange = viewModel::setPureBlack,
        onLibraryFolderPicked = viewModel::setLocalLibraryUri,
        onReaderModeChange = viewModel::setDefaultReaderMode,
        onVolumeKeysChange = viewModel::setVolumeKeysNavigation,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    settings: UserSettings?,
    gemini: GeminiConfig?,
    openAi: OpenAiConfig?,
    onTranslationEngineChange: (TranslationEngine) -> Unit,
    onTranslationLangChange: (String) -> Unit,
    onTranslateByDefaultChange: (Boolean) -> Unit,
    onTranslateOnDownloadChange: (Boolean) -> Unit,
    onUpdateIntervalChange: (Int) -> Unit,
    onUpdateWifiOnlyChange: (Boolean) -> Unit,
    onAutoDownloadNewChange: (Boolean) -> Unit,
    onGeminiChange: (GeminiConfig) -> Unit,
    onOpenAiChange: (OpenAiConfig) -> Unit,
    komgaUrl: String?,
    onOpenKomga: () -> Unit,
    onOpenBackup: () -> Unit,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onPureBlackChange: (Boolean) -> Unit,
    onLibraryFolderPicked: (String) -> Unit,
    onReaderModeChange: (ReaderMode) -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
) {
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showReaderModeDialog by rememberSaveable { mutableStateOf(false) }
    var showEngineDialog by rememberSaveable { mutableStateOf(false) }
    var showTargetDialog by rememberSaveable { mutableStateOf(false) }
    var showIntervalDialog by rememberSaveable { mutableStateOf(false) }
    val pickFolder = rememberLibraryFolderPicker { onLibraryFolderPicked(it.toString()) }
    val language = remember(showLanguageDialog) { AppLanguage.current() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.feature_settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (settings == null) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.feature_settings_section_appearance))

            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_settings_theme)) },
                supportingContent = {
                    ThemeModeSelector(
                        selected = settings.themeMode,
                        onSelect = onThemeModeChange,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchItem(
                    title = stringResource(R.string.feature_settings_dynamic_color),
                    summary = stringResource(R.string.feature_settings_dynamic_color_summary),
                    checked = settings.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                )
            }
            SwitchItem(
                title = stringResource(R.string.feature_settings_pure_black),
                summary = stringResource(R.string.feature_settings_pure_black_summary),
                checked = settings.pureBlack,
                onCheckedChange = onPureBlackChange,
            )

            SectionHeader(stringResource(R.string.feature_settings_section_general))
            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_settings_language)) },
                supportingContent = { Text(stringResource(language.label)) },
                modifier = Modifier.clickable { showLanguageDialog = true },
            )

            SectionHeader(stringResource(R.string.feature_settings_section_library))
            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_settings_local_folder)) },
                supportingContent = {
                    Text(
                        settings.localLibraryUri?.let(::folderLabel)
                            ?: stringResource(R.string.feature_settings_local_folder_not_set),
                    )
                },
                modifier = Modifier.clickable(onClick = pickFolder),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_settings_komga_title)) },
                supportingContent = { Text(komgaUrl ?: stringResource(R.string.feature_settings_komga_not_set)) },
                modifier = Modifier.clickable(onClick = onOpenKomga),
            )

            SectionHeader(stringResource(R.string.feature_settings_section_updates))
            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_settings_update_interval)) },
                supportingContent = { Text(intervalLabel(settings.libraryUpdateIntervalHours)) },
                modifier = Modifier.clickable { showIntervalDialog = true },
            )
            if (settings.libraryUpdateIntervalHours > 0) {
                SwitchItem(
                    title = stringResource(R.string.feature_settings_update_wifi_only),
                    summary = stringResource(R.string.feature_settings_update_wifi_only_summary),
                    checked = settings.libraryUpdateWifiOnly,
                    onCheckedChange = onUpdateWifiOnlyChange,
                )
            }
            SwitchItem(
                title = stringResource(R.string.feature_settings_auto_download),
                summary = stringResource(R.string.feature_settings_auto_download_summary),
                checked = settings.autoDownloadNewChapters,
                onCheckedChange = onAutoDownloadNewChange,
            )

            SectionHeader(stringResource(R.string.feature_settings_section_reader))
            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_settings_reader_mode)) },
                supportingContent = { Text(stringResource(settings.defaultReaderMode.labelRes)) },
                modifier = Modifier.clickable { showReaderModeDialog = true },
            )
            SwitchItem(
                title = stringResource(R.string.feature_settings_volume_keys),
                summary = stringResource(R.string.feature_settings_volume_keys_summary),
                checked = settings.volumeKeysNavigation,
                onCheckedChange = onVolumeKeysChange,
            )

            SectionHeader(stringResource(R.string.feature_settings_section_translation))
            TranslationSection(
                settings = settings,
                gemini = gemini,
                openAi = openAi,
                onPickEngine = { showEngineDialog = true },
                onPickLanguage = { showTargetDialog = true },
                onTranslateByDefaultChange = onTranslateByDefaultChange,
                onTranslateOnDownloadChange = onTranslateOnDownloadChange,
                onGeminiChange = onGeminiChange,
                onOpenAiChange = onOpenAiChange,
                switchItem = { title, summary, checked, onChange -> SwitchItem(title, summary, checked, onChange) },
            )

            SectionHeader(stringResource(R.string.feature_settings_backup_title))
            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_settings_backup_title)) },
                supportingContent = { Text(stringResource(R.string.feature_settings_backup_create_summary)) },
                modifier = Modifier.clickable(onClick = onOpenBackup),
            )
        }
    }

    if (showIntervalDialog && settings != null) {
        ChoiceDialog(
            title = stringResource(R.string.feature_settings_update_interval),
            options = UPDATE_INTERVALS,
            selected = settings.libraryUpdateIntervalHours,
            label = { intervalLabel(it) },
            onSelect = {
                showIntervalDialog = false
                onUpdateIntervalChange(it)
            },
            onDismiss = { showIntervalDialog = false },
        )
    }

    if (showEngineDialog && settings != null) {
        ChoiceDialog(
            title = stringResource(R.string.feature_settings_translation_engine),
            options = TranslationEngine.entries.filter { it.implemented },
            selected = settings.translationEngine,
            label = { it.label() },
            onSelect = {
                showEngineDialog = false
                onTranslationEngineChange(it)
            },
            onDismiss = { showEngineDialog = false },
        )
    }
    if (showTargetDialog && settings != null) {
        ChoiceDialog(
            title = stringResource(R.string.feature_settings_translation_lang),
            options = TRANSLATION_TARGETS,
            selected = settings.translationTargetLang,
            label = { languageLabel(it) },
            onSelect = {
                showTargetDialog = false
                onTranslationLangChange(it)
            },
            onDismiss = { showTargetDialog = false },
        )
    }

    if (showReaderModeDialog && settings != null) {
        ChoiceDialog(
            title = stringResource(R.string.feature_settings_reader_mode),
            options = ReaderMode.entries,
            selected = settings.defaultReaderMode,
            label = { stringResource(it.labelRes) },
            onSelect = {
                showReaderModeDialog = false
                onReaderModeChange(it)
            },
            onDismiss = { showReaderModeDialog = false },
        )
    }

    if (showLanguageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.feature_settings_language),
            options = AppLanguage.entries,
            selected = language,
            label = { stringResource(it.label) },
            onSelect = {
                showLanguageDialog = false
                it.apply()
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.selectable(
            selected = checked,
            role = Role.Switch,
            onClick = { onCheckedChange(!checked) },
        ),
    )
}

@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = mapOf(
        ThemeMode.System to R.string.feature_settings_theme_system,
        ThemeMode.Light to R.string.feature_settings_theme_light,
        ThemeMode.Dark to R.string.feature_settings_theme_dark,
    )
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
            ) {
                Text(stringResource(labels.getValue(mode)))
            }
        }
    }
}

@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    ListItem(
                        headlineContent = { Text(label(option)) },
                        leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                        modifier = Modifier.selectable(
                            selected = option == selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_settings_cancel)) }
        },
    )
}

/** Hours between library updates offered in settings; 0 is "off". */
private val UPDATE_INTERVALS = listOf(0, 6, 12, 24, 48)

@Composable
private fun intervalLabel(hours: Int): String =
    if (hours <= 0) stringResource(R.string.feature_settings_update_off) else stringResource(R.string.feature_settings_update_every, hours)

/** "primary:Manga/Shelf" → "Manga/Shelf": the tree id is the most readable thing SAF gives us. */
private fun folderLabel(uri: String): String =
    runCatching { DocumentsContract.getTreeDocumentId(uri.toUri()).substringAfter(':') }
        .getOrNull()
        ?.ifEmpty { null }
        ?: uri

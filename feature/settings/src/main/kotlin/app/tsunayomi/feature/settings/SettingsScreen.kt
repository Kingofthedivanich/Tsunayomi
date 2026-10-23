package app.tsunayomi.feature.settings

import android.os.Build
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
import app.tsunayomi.core.model.ThemeMode
import app.tsunayomi.core.model.UserSettings

@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsContent(
        settings = settings,
        onBack = onBack,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onPureBlackChange = viewModel::setPureBlack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    settings: UserSettings?,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onPureBlackChange: (Boolean) -> Unit,
) {
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
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
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            selected = language,
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
private fun LanguageDialog(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_settings_language)) },
        text = {
            Column {
                AppLanguage.entries.forEach { language ->
                    ListItem(
                        headlineContent = { Text(stringResource(language.label)) },
                        leadingContent = { RadioButton(selected = language == selected, onClick = null) },
                        modifier = Modifier.selectable(
                            selected = language == selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(language) },
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

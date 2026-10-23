package app.tsunayomi.feature.extensions

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import androidx.preference.DialogPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import app.tsunayomi.extension.manager.ExtensionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.preferenceKey
import javax.inject.Inject

@HiltViewModel
internal class SourcePreferencesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    manager: ExtensionManager,
) : ViewModel() {
    val source: ConfigurableSource? =
        manager.extensionSource(savedStateHandle.toRoute<ExtensionSourceSettingsRoute>().sourceId) as? ConfigurableSource
}

/**
 * Shows the androidx.preference screen an extension builds in `setupPreferenceScreen`, drawn with Compose instead
 * of a PreferenceFragment. Values are stored in the source's own SharedPreferences, where the extension reads them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourcePreferencesScreen(onBack: () -> Unit, viewModel: SourcePreferencesViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val source = viewModel.source
    val screen = remember(source) { source?.let { buildScreen(context, it) } }
    // Preferences are plain objects; bump this after a change so summaries are read again.
    var version by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<DialogPreference?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(source?.name ?: stringResource(R.string.feature_extensions_source_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.feature_extensions_back))
                    }
                },
            )
        },
    ) { padding ->
        val items = remember(screen, version) { screen?.let(::flatten).orEmpty().filter { it.isVisible } }
        LazyColumn(contentPadding = padding) {
            items(items) { preference ->
                val title = preference.title?.toString().orEmpty()
                val summary = runCatching { preference.summary?.toString() }.getOrNull()
                when (preference) {
                    is PreferenceGroup -> Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                    is TwoStatePreference -> ListItem(
                        headlineContent = { Text(title) },
                        supportingContent = summary?.let { { Text(it) } },
                        trailingContent = {
                            Switch(
                                checked = preference.isChecked,
                                enabled = preference.isEnabled,
                                onCheckedChange = { checked ->
                                    if (preference.callChangeListener(checked)) preference.isChecked = checked
                                    version++
                                },
                            )
                        },
                    )
                    else -> ListItem(
                        headlineContent = { Text(title) },
                        supportingContent = summary?.let { { Text(it) } },
                        modifier = Modifier.clickable(enabled = preference.isEnabled && preference is DialogPreference) {
                            editing = preference as DialogPreference
                        },
                    )
                }
            }
        }
    }

    editing?.let { preference ->
        PreferenceDialog(
            preference = preference,
            onDone = {
                editing = null
                version++
            },
        )
    }
}

@SuppressLint("RestrictedApi")
private fun buildScreen(context: Context, source: ConfigurableSource): PreferenceScreen {
    val manager = PreferenceManager(context)
    manager.sharedPreferencesName = source.preferenceKey()
    return manager.createPreferenceScreen(context).also(source::setupPreferenceScreen)
}

private fun flatten(group: PreferenceGroup): List<Preference> = (0 until group.preferenceCount).flatMap { index ->
    when (val preference = group.getPreference(index)) {
        is PreferenceGroup -> listOf(preference) + flatten(preference)
        else -> listOf(preference)
    }
}

@Composable
private fun PreferenceDialog(preference: DialogPreference, onDone: () -> Unit) {
    val title = (preference.dialogTitle ?: preference.title)?.toString().orEmpty()
    when (preference) {
        is EditTextPreference -> {
            var text by remember { mutableStateOf(preference.text.orEmpty()) }
            AlertDialog(
                onDismissRequest = onDone,
                title = { Text(title) },
                text = {
                    Column {
                        preference.dialogMessage?.let { Text(it.toString(), Modifier.padding(bottom = 8.dp)) }
                        OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (preference.callChangeListener(text)) preference.text = text
                        onDone()
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.feature_extensions_cancel)) } },
            )
        }
        is ListPreference -> AlertDialog(
            onDismissRequest = onDone,
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    preference.entries.orEmpty().forEachIndexed { index, entry ->
                        val value = preference.entryValues[index].toString()
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (preference.callChangeListener(value)) preference.value = value
                                onDone()
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = preference.value == value, onClick = null, modifier = Modifier.padding(12.dp))
                            Text(entry.toString())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.feature_extensions_cancel)) } },
        )
        is MultiSelectListPreference -> {
            var selected by remember { mutableStateOf(preference.values.toSet()) }
            AlertDialog(
                onDismissRequest = onDone,
                title = { Text(title) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        preference.entries.orEmpty().forEachIndexed { index, entry ->
                            val value = preference.entryValues[index].toString()
                            val checked = value in selected
                            Row(
                                Modifier.fillMaxWidth().clickable { selected = if (checked) selected - value else selected + value },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                                Text(entry.toString())
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (preference.callChangeListener(selected)) preference.values = selected
                        onDone()
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.feature_extensions_cancel)) } },
            )
        }
        else -> onDone()
    }
}

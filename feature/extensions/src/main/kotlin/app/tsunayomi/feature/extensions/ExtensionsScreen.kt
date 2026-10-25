package app.tsunayomi.feature.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tsunayomi.core.designsystem.component.EmptyState
import app.tsunayomi.extension.manager.ExtensionRepo
import app.tsunayomi.extension.manager.InstalledExtension
import coil3.compose.AsyncImage
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtensionsScreen(onBack: () -> Unit, viewModel: ExtensionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showRepos by rememberSaveable { mutableStateOf(false) }
    var showLanguages by rememberSaveable { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf<ExtensionRow?>(null) }
    var confirmInstallAll by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messageEvents.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_extensions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.feature_extensions_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showLanguages = true }) {
                        Icon(Icons.Outlined.Language, stringResource(R.string.feature_extensions_languages))
                    }
                    IconButton(onClick = { showRepos = true }) {
                        Icon(Icons.Outlined.Storage, stringResource(R.string.feature_extensions_repos))
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.feature_extensions_refresh))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.repos.isEmpty() && state.installed.isEmpty() && state.updates.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    icon = Icons.Outlined.Extension,
                    title = stringResource(R.string.feature_extensions_no_repos_title),
                    message = stringResource(R.string.feature_extensions_no_repos_message),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { showRepos = true }, modifier = Modifier.padding(bottom = 32.dp)) {
                    Text(stringResource(R.string.feature_extensions_add_repo))
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            placeholder = { Text(stringResource(R.string.feature_extensions_search)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    val installingAll = ExtensionsViewModel.INSTALL_ALL_KEY in state.busy
                    section(
                        R.string.feature_extensions_section_updates,
                        state.updates,
                        state.busy,
                        headerAction = if (state.updates.size > 1) {
                            {
                                TextButton(onClick = { viewModel.installAll(state.updates) }, enabled = !installingAll) {
                                    Text(stringResource(R.string.feature_extensions_update_all))
                                }
                            }
                        } else {
                            null
                        },
                    ) { row ->
                        FilledTonalButton(onClick = { viewModel.install(row) }) {
                            Text(stringResource(R.string.feature_extensions_update))
                        }
                    }
                    section(R.string.feature_extensions_section_installed, state.installed, state.busy) { row ->
                        IconButton(onClick = { confirmUninstall = row }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.feature_extensions_uninstall))
                        }
                    }
                    section(
                        R.string.feature_extensions_section_available,
                        state.available,
                        state.busy,
                        headerAction = if (state.available.size > 1) {
                            {
                                TextButton(onClick = { confirmInstallAll = true }, enabled = !installingAll) {
                                    Text(stringResource(R.string.feature_extensions_install_all))
                                }
                            }
                        } else {
                            null
                        },
                    ) { row ->
                        TextButton(onClick = { viewModel.install(row) }) {
                            Text(stringResource(R.string.feature_extensions_install))
                        }
                    }
                }
            }
        }
    }

    if (showRepos) {
        ReposDialog(
            repos = state.repos,
            busy = ExtensionsViewModel.REPO_KEY in state.busy,
            onAdd = viewModel::addRepo,
            onRemove = viewModel::removeRepo,
            onDismiss = { showRepos = false },
        )
    }
    if (showLanguages) {
        LanguagesDialog(
            languages = (state.allLanguages + state.languages).distinct().sorted(),
            enabled = state.languages,
            showNsfw = state.showNsfw,
            onToggle = viewModel::setLanguageEnabled,
            onShowNsfw = viewModel::setShowNsfw,
            onDismiss = { showLanguages = false },
        )
    }
    if (confirmInstallAll) {
        AlertDialog(
            onDismissRequest = { confirmInstallAll = false },
            title = { Text(stringResource(R.string.feature_extensions_install_all_title, state.available.size)) },
            text = { Text(stringResource(R.string.feature_extensions_install_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.installAll(state.available)
                    confirmInstallAll = false
                }) { Text(stringResource(R.string.feature_extensions_install_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmInstallAll = false }) { Text(stringResource(R.string.feature_extensions_cancel)) }
            },
        )
    }
    confirmUninstall?.let { row ->
        AlertDialog(
            onDismissRequest = { confirmUninstall = null },
            title = { Text(stringResource(R.string.feature_extensions_uninstall_title, row.name)) },
            text = { Text(stringResource(R.string.feature_extensions_uninstall_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.uninstall(row)
                    confirmUninstall = null
                }) { Text(stringResource(R.string.feature_extensions_uninstall)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = null }) { Text(stringResource(R.string.feature_extensions_cancel)) }
            },
        )
    }
}

private fun LazyListScope.section(
    title: Int,
    rows: List<ExtensionRow>,
    busy: Set<String>,
    headerAction: (@Composable () -> Unit)? = null,
    action: @Composable (ExtensionRow) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "header-$title") {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            headerAction?.invoke()
        }
    }
    items(rows, key = { "$title-${it.pkgName}" }) { row ->
        ListItem(
            leadingContent = {
                if (row.iconUri != null) {
                    AsyncImage(model = row.iconUri, contentDescription = null, modifier = Modifier.size(40.dp))
                } else {
                    Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(40.dp))
                }
            },
            headlineContent = { Text(row.name) },
            supportingContent = {
                val failure = (row.installed as? InstalledExtension.Failed)
                Text(
                    failure?.let { failureText(it) }
                        ?: listOfNotNull(
                            languageName(row.lang),
                            row.available?.takeIf { row.installed != null }?.let { "${row.version} → ${it.versionName}" } ?: row.version,
                            if (row.nsfw) "18+" else null,
                        ).joinToString(" · "),
                    color = if (failure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                if (row.pkgName in busy) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    action(row)
                }
            },
        )
    }
}

@Composable
private fun failureText(failure: InstalledExtension.Failed): String = when (failure.reason) {
    InstalledExtension.Failed.Reason.UnsupportedLib -> stringResource(R.string.feature_extensions_failed_lib)
    InstalledExtension.Failed.Reason.Untrusted -> stringResource(R.string.feature_extensions_failed_untrusted)
    InstalledExtension.Failed.Reason.Broken -> stringResource(R.string.feature_extensions_failed_broken) +
        failure.detail?.let { ": $it" }.orEmpty()
}

@Composable
private fun ReposDialog(
    repos: List<ExtensionRepo>,
    busy: Boolean,
    onAdd: (String) -> Unit,
    onRemove: (ExtensionRepo) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_extensions_repos)) },
        text = {
            Column {
                repos.forEach { repo ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(repo.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                repo.website ?: repo.indexUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onRemove(repo) }, enabled = !busy) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.feature_extensions_remove_repo))
                        }
                    }
                }
                if (repos.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.feature_extensions_repo_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.feature_extensions_repo_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Box(contentAlignment = Alignment.Center) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    TextButton(
                        onClick = {
                            onAdd(url)
                            url = ""
                        },
                        enabled = url.isNotBlank(),
                    ) { Text(stringResource(R.string.feature_extensions_add_repo)) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_extensions_close)) } },
    )
}

@Composable
private fun LanguagesDialog(
    languages: List<String>,
    enabled: Set<String>,
    showNsfw: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onShowNsfw: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_extensions_languages)) },
        text = {
            LazyColumn {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { onShowNsfw(!showNsfw) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.feature_extensions_show_nsfw), Modifier.weight(1f))
                        Switch(checked = showNsfw, onCheckedChange = onShowNsfw)
                    }
                    HorizontalDivider()
                }
                items(languages) { lang ->
                    val checked = lang in enabled
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(lang, !checked) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggle(lang, it) })
                        Text(languageName(lang))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_extensions_close)) } },
    )
}

@Composable
private fun languageName(lang: String): String = when (lang) {
    "all", "multi", "" -> stringResource(R.string.feature_extensions_lang_multi)
    "other" -> stringResource(R.string.feature_extensions_lang_other)
    else -> {
        val uiLocale = LocalConfiguration.current.locales[0]
        Locale.forLanguageTag(lang).getDisplayName(uiLocale).replaceFirstChar { it.titlecase(uiLocale) }.ifEmpty { lang }
    }
}

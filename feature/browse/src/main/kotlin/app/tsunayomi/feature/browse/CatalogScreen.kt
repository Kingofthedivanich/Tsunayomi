package app.tsunayomi.feature.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tsunayomi.core.designsystem.component.MangaGrid
import app.tsunayomi.core.designsystem.component.MangaGridItem
import app.tsunayomi.core.designsystem.component.MessageWithAction
import app.tsunayomi.core.designsystem.component.rememberLibraryFolderPicker
import app.tsunayomi.source.api.SourceIds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogScreen(
    onBack: () -> Unit,
    onOpenManga: (Long) -> Unit,
    onOpenSourceSettings: (Long) -> Unit,
    onOpenWebView: (url: String, sourceId: Long) -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.openMangaEvents.collect(onOpenManga) }
    val pickFolder = rememberLibraryFolderPicker { viewModel.setLibraryFolder(it.toString()) }

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val isLocal = viewModel.source.id == SourceIds.LOCAL
    val webUrl = remember(viewModel.source) { viewModel.source.webUrl() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        SearchField(
                            query = query,
                            onQueryChange = { query = it },
                            onSearch = { viewModel.search(query) },
                        )
                    } else {
                        val lookup = state.lookup
                        if (lookup != null) {
                            Column {
                                Text(lookupTitle(lookup), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    viewModel.source.displayName(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(viewModel.source.displayName())
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.feature_browse_back))
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = {
                            searching = false
                            query = ""
                            viewModel.search("")
                        }) { Icon(Icons.Outlined.Close, stringResource(R.string.feature_browse_close_search)) }
                    } else {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.feature_browse_search))
                        }
                    }
                    if (!isLocal) {
                        IconButton(onClick = {
                            viewModel.loadTags()
                            showTags = true
                        }) {
                            Icon(Icons.Outlined.Sell, stringResource(R.string.feature_browse_tags_title))
                        }
                    }
                    if (viewModel.hasFilters) {
                        IconButton(onClick = { showFilters = true }) {
                            Icon(Icons.Outlined.FilterList, stringResource(R.string.feature_browse_filters))
                        }
                    }
                    if (viewModel.source.hasSettings) {
                        IconButton(onClick = { onOpenSourceSettings(viewModel.source.id) }) {
                            Icon(Icons.Outlined.Settings, stringResource(R.string.feature_browse_source_settings))
                        }
                    }
                    if (webUrl != null) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Outlined.MoreVert, stringResource(R.string.feature_browse_more_options))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.feature_browse_open_in_webview)) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenWebView(webUrl, viewModel.source.id)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (viewModel.source.supportsLatest && state.query == null && !state.filtersApplied && state.lookup == null) {
                ListingChips(selected = state.listing, onSelect = viewModel::setListing)
            }
            PullToRefreshBox(
                isRefreshing = state.loading && state.items.isNotEmpty(),
                onRefresh = viewModel::reload,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.notConfigured && isLocal -> MessageWithAction(
                        title = stringResource(R.string.feature_browse_local_not_configured_title),
                        message = stringResource(R.string.feature_browse_local_not_configured_message),
                        icon = Icons.Outlined.FolderOpen,
                        actionLabel = stringResource(R.string.feature_browse_choose_folder),
                        onAction = pickFolder,
                    )
                    state.notConfigured -> MessageWithAction(
                        title = stringResource(R.string.feature_browse_server_not_configured_title),
                        message = stringResource(R.string.feature_browse_server_not_configured_message),
                        icon = Icons.Outlined.Dns,
                        actionLabel = stringResource(R.string.feature_browse_configure),
                        onAction = { onOpenSourceSettings(viewModel.source.id) },
                    )
                    state.error != null && state.items.isEmpty() -> {
                        if (state.isCloudflareError && webUrl != null) {
                            MessageWithAction(
                                title = stringResource(R.string.feature_browse_cloudflare_title),
                                message = state.error,
                                actionLabel = stringResource(R.string.feature_browse_open_in_webview),
                                onAction = { onOpenWebView(webUrl, viewModel.source.id) },
                                secondaryActionLabel = stringResource(R.string.feature_browse_retry),
                                onSecondaryAction = viewModel::reload,
                            )
                        } else {
                            MessageWithAction(
                                title = stringResource(R.string.feature_browse_error_title),
                                message = state.error,
                                actionLabel = stringResource(R.string.feature_browse_retry),
                                onAction = viewModel::reload,
                            )
                        }
                    }
                    state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.lookupUnsupported -> MessageWithAction(
                        title = stringResource(
                            if (state.lookup is CatalogLookup.Author) {
                                R.string.feature_browse_author_unsupported
                            } else {
                                R.string.feature_browse_tag_unsupported
                            },
                        ),
                        icon = Icons.Outlined.SearchOff,
                    )
                    state.items.isEmpty() -> MessageWithAction(
                        title = stringResource(R.string.feature_browse_nothing_found),
                        message = if (isLocal) {
                            stringResource(R.string.feature_browse_local_empty_message)
                        } else {
                            null
                        },
                        icon = Icons.Outlined.SearchOff,
                    )
                    else -> MangaGrid(
                        items = state.items.map { MangaGridItem(it.url, it.title, it.coverUrl) },
                        onClick = { item -> state.items.firstOrNull { it.url == item.key }?.let(viewModel::onMangaClick) },
                        onLoadMore = viewModel::loadMore,
                        footer = {
                            if (state.loading) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showTags) {
        val choices by viewModel.tagChoices.collectAsStateWithLifecycle()
        TagSheet(
            choices = choices,
            initiallySelected = (state.lookup as? CatalogLookup.Tags)?.names.orEmpty(),
            onSearch = {
                showTags = false
                // Tags replace a text search, so its field closes and the title shows the tags.
                searching = false
                query = ""
                viewModel.searchTags(it)
            },
            onDismiss = { showTags = false },
        )
    }
    if (showFilters) {
        CatalogFilterSheet(viewModel, state.filterVersion, onDismiss = { showFilters = false })
    }
}

@Composable
private fun CatalogFilterSheet(viewModel: CatalogViewModel, version: Int, onDismiss: () -> Unit) {
    FilterSheet(
        filters = viewModel.filters,
        version = version,
        onEdit = viewModel::editFilters,
        onReset = viewModel::resetFilters,
        onApply = {
            onDismiss()
            viewModel.applyFilters()
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun ListingChips(selected: CatalogListing, onSelect: (CatalogListing) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == CatalogListing.Popular,
            onClick = { onSelect(CatalogListing.Popular) },
            label = { Text(stringResource(R.string.feature_browse_popular)) },
        )
        FilterChip(
            selected = selected == CatalogListing.Latest,
            onClick = { onSelect(CatalogListing.Latest) },
            label = { Text(stringResource(R.string.feature_browse_latest)) },
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.feature_browse_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            onSearch()
        }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}

@Composable
private fun lookupTitle(lookup: CatalogLookup): String = when (lookup) {
    is CatalogLookup.Tags -> stringResource(R.string.feature_browse_lookup_tag, lookup.names.joinToString())
    is CatalogLookup.Author -> stringResource(R.string.feature_browse_lookup_author, lookup.name)
}

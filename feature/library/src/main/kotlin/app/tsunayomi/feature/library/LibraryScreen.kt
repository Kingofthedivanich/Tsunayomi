package app.tsunayomi.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tsunayomi.core.designsystem.component.EmptyState
import app.tsunayomi.core.designsystem.component.MangaGrid
import app.tsunayomi.core.designsystem.component.MangaGridItem
import app.tsunayomi.core.model.LibraryManga
import app.tsunayomi.core.model.LibrarySort
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(
    onOpenManga: (Long) -> Unit,
    onOpenReader: (mangaId: Long, chapterId: Long) -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updating by viewModel.updating.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is LibraryEvent.OpenReader -> onOpenReader(event.mangaId, event.chapterId)
                is LibraryEvent.OpenManga -> onOpenManga(event.mangaId)
            }
        }
    }
    var searching by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        LibrarySearchField(state.query, viewModel::setQuery)
                    } else {
                        Text(stringResource(R.string.feature_library_title))
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = {
                            searching = false
                            viewModel.setQuery("")
                        }) { Icon(Icons.Outlined.Close, stringResource(R.string.feature_library_close_search)) }
                    } else {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.feature_library_search))
                        }
                    }
                    SortMenu(state.sort, state.ascending, viewModel::setSort)
                    IconButton(onClick = onOpenCategories) {
                        Icon(Icons.AutoMirrored.Outlined.Label, stringResource(R.string.feature_library_categories))
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        if (state.totalCount == 0) {
            EmptyState(
                icon = Icons.Outlined.CollectionsBookmark,
                title = stringResource(R.string.feature_library_empty_title),
                message = stringResource(R.string.feature_library_empty_message),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        PullToRefreshBox(
            isRefreshing = updating,
            onRefresh = viewModel::updateLibrary,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        Column {
            val tabs = state.tabs
            val pagerState = rememberPagerState { tabs.size }
            val scope = rememberCoroutineScope()
            if (tabs.size > 1 || tabs.firstOrNull()?.categoryId != null) {
                PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage.coerceAtMost(tabs.lastIndex), edgePadding = 0.dp) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text("${tab.name ?: stringResource(R.string.feature_library_default_category)} (${tab.items.size})")
                            },
                        )
                    }
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { tabs[it].categoryId ?: -1L }) { page ->
                val tab = tabs[page]
                if (tab.items.isEmpty()) {
                    EmptyState(
                        icon = if (state.query.isNotBlank()) Icons.Outlined.SearchOff else Icons.Outlined.CollectionsBookmark,
                        title = stringResource(
                            if (state.query.isNotBlank()) R.string.feature_library_nothing_found else R.string.feature_library_category_empty,
                        ),
                    )
                } else {
                    Box(Modifier.fillMaxSize()) {
                        MangaGrid(
                            items = tab.items.map { it.toGridItem() },
                            onClick = { onOpenManga(it.key.toLong()) },
                            onContinue = { viewModel.continueReading(it.key.toLong()) },
                        )
                    }
                }
            }
        }
        }
    }
}

private fun LibraryManga.toGridItem() = MangaGridItem(
    key = manga.id.toString(),
    title = manga.title,
    coverUrl = manga.coverUrl,
    badge = unreadCount.takeIf { it > 0 }?.toString(),
)

@Composable
private fun SortMenu(sort: LibrarySort, ascending: Boolean, onSelect: (LibrarySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.feature_library_sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    trailingIcon = {
                        if (option == sort) {
                            Icon(if (ascending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, null)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

private val LibrarySort.labelRes: Int
    get() = when (this) {
        LibrarySort.Title -> R.string.feature_library_sort_title
        LibrarySort.LastRead -> R.string.feature_library_sort_last_read
        LibrarySort.DateAdded -> R.string.feature_library_sort_date_added
        LibrarySort.Unread -> R.string.feature_library_sort_unread
    }

@Composable
private fun LibrarySearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.feature_library_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
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

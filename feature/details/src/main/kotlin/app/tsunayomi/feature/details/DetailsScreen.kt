package app.tsunayomi.feature.details

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tsunayomi.core.designsystem.component.COVER_ASPECT_RATIO
import app.tsunayomi.core.designsystem.component.CoverImage
import app.tsunayomi.core.model.Chapter
import app.tsunayomi.core.model.Manga
import app.tsunayomi.core.model.MangaStatus
import app.tsunayomi.source.api.SourceIds
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailsScreen(
    onBack: () -> Unit,
    onRead: (mangaId: Long, chapterId: Long) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.feature_details_back))
                    }
                },
            )
        },
        floatingActionButton = {
            val manga = state.manga
            val chapter = state.continueChapter
            if (manga != null && chapter != null) {
                ExtendedFloatingActionButton(
                    onClick = { onRead(manga.id, chapter.id) },
                    icon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                    text = {
                        Text(
                            stringResource(
                                if (state.hasProgress) R.string.feature_details_continue else R.string.feature_details_start,
                            ),
                        )
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val manga = state.manga ?: return@Scaffold
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                item { Header(manga, state.sourceName, onToggleFavorite = viewModel::toggleFavorite) }
                item {
                    Text(
                        text = pluralStringResource(R.plurals.feature_details_chapter_count, state.chapters.size, state.chapters.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(state.chapters, key = { it.id }) { chapter ->
                    ChapterRow(
                        chapter = chapter,
                        onClick = { onRead(manga.id, chapter.id) },
                        onSetRead = { viewModel.setRead(chapter, it) },
                        onReadUpTo = { viewModel.setReadUpTo(chapter) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(manga: Manga, sourceName: String?, onToggleFavorite: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row {
            CoverImage(
                url = manga.coverUrl,
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(COVER_ASPECT_RATIO)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(manga.title, style = MaterialTheme.typography.titleLarge)
                listOfNotNull(manga.author, manga.artist?.takeIf { it != manga.author })
                    .takeIf { it.isNotEmpty() }
                    ?.let { Text(it.joinToString(), style = MaterialTheme.typography.bodyMedium) }
                statusLabel(manga.status)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val sourceLabel = if (manga.sourceId == SourceIds.LOCAL) {
                    stringResource(R.string.feature_details_local_source)
                } else {
                    sourceName
                }
                if (sourceLabel != null) {
                    Text(sourceLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onToggleFavorite) {
            Icon(
                if (manga.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    if (manga.favorite) R.string.feature_details_in_library else R.string.feature_details_add_to_library,
                ),
            )
        }
        manga.description?.takeIf { it.isNotBlank() }?.let { description ->
            var expanded by rememberSaveable { mutableStateOf(false) }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .animateContentSize()
                    .clickable { expanded = !expanded },
            )
        }
        if (manga.genres.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                manga.genres.forEach { genre -> AssistChip(onClick = {}, label = { Text(genre) }) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(
    chapter: Chapter,
    onClick: () -> Unit,
    onSetRead: (Boolean) -> Unit,
    onReadUpTo: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        ListItem(
            headlineContent = { Text(chapter.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                val parts = listOfNotNull(
                    chapter.uploadedAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) },
                    chapter.scanlator,
                    if (!chapter.read && chapter.lastPageRead > 0) {
                        stringResource(R.string.feature_details_page_progress, chapter.lastPageRead + 1)
                    } else {
                        null
                    },
                )
                if (parts.isNotEmpty()) Text(parts.joinToString(" • "))
            },
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (chapter.read) 0.5f else 1f)
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(if (chapter.read) R.string.feature_details_mark_unread else R.string.feature_details_mark_read))
                },
                onClick = {
                    menuOpen = false
                    onSetRead(!chapter.read)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_details_mark_read_up_to)) },
                onClick = {
                    menuOpen = false
                    onReadUpTo()
                },
            )
        }
    }
}

@Composable
private fun statusLabel(status: MangaStatus): String? = when (status) {
    MangaStatus.Unknown -> null
    MangaStatus.Ongoing -> stringResource(R.string.feature_details_status_ongoing)
    MangaStatus.Completed -> stringResource(R.string.feature_details_status_completed)
    MangaStatus.Hiatus -> stringResource(R.string.feature_details_status_hiatus)
    MangaStatus.Cancelled -> stringResource(R.string.feature_details_status_cancelled)
}

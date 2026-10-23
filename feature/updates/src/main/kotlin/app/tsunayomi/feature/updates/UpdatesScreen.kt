package app.tsunayomi.feature.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.tsunayomi.core.designsystem.component.COVER_ASPECT_RATIO
import app.tsunayomi.core.designsystem.component.CoverImage
import app.tsunayomi.core.designsystem.component.EmptyState
import app.tsunayomi.core.model.ChapterDownload
import app.tsunayomi.core.model.DownloadState
import app.tsunayomi.core.model.UpdateItem
import app.tsunayomi.data.LibraryRepository
import app.tsunayomi.data.UserSettingsRepository
import app.tsunayomi.downloads.DownloadManager
import app.tsunayomi.updates.LibraryUpdateScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject

@Serializable
data object UpdatesRoute

fun NavGraphBuilder.updatesScreen(
    onOpenManga: (mangaId: Long) -> Unit,
    onOpenReader: (mangaId: Long, chapterId: Long) -> Unit,
) {
    composable<UpdatesRoute> { UpdatesScreen(onOpenManga, onOpenReader) }
}

@HiltViewModel
internal class UpdatesViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    settingsRepository: UserSettingsRepository,
    private val downloadManager: DownloadManager,
    private val scheduler: LibraryUpdateScheduler,
) : ViewModel() {
    /** Null while loading, so the empty state does not flash on start. */
    val items: StateFlow<List<UpdateItem>?> = libraryRepository.observeUpdates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    val downloads: StateFlow<Map<Long, ChapterDownload>> = downloadManager.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val updating: StateFlow<Boolean> = scheduler.isRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val intervalHours: StateFlow<Int> = settingsRepository.settings.map { it.libraryUpdateIntervalHours }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 12)

    fun refresh() = scheduler.runNow()

    fun onDownloadClick(item: UpdateItem) {
        val chapter = item.chapter
        viewModelScope.launch {
            when (downloads.value[chapter.id]?.state) {
                null -> downloadManager.enqueue(chapter.mangaId, listOf(chapter.id))
                DownloadState.Failed -> downloadManager.retry(chapter.id)
                DownloadState.Queued, DownloadState.Downloading, DownloadState.Translating ->
                    downloadManager.remove(chapter.mangaId, listOf(chapter.id))
                DownloadState.Done -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdatesScreen(
    onOpenManga: (Long) -> Unit,
    onOpenReader: (mangaId: Long, chapterId: Long) -> Unit,
    viewModel: UpdatesViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val updating by viewModel.updating.collectAsStateWithLifecycle()
    val interval by viewModel.intervalHours.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_updates_title)) },
                actions = {
                    if (updating) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Outlined.Refresh, stringResource(R.string.feature_updates_refresh))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val list = items ?: return@Scaffold
        PullToRefreshBox(
            isRefreshing = updating,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (list.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.NewReleases,
                    title = stringResource(R.string.feature_updates_empty_title),
                    message = if (interval > 0) {
                        stringResource(R.string.feature_updates_empty_message, interval)
                    } else {
                        stringResource(R.string.feature_updates_empty_message_off)
                    },
                )
                return@PullToRefreshBox
            }
            val zone = remember { ZoneId.systemDefault() }
            val groups = remember(list) {
                list.groupBy { Instant.ofEpochMilli(it.fetchedAt).atZone(zone).toLocalDate() }.toList()
            }
            val today = remember { LocalDate.now(zone) }
            val dateFormat = remember { DateFormat.getDateInstance(DateFormat.LONG) }
            LazyColumn {
                groups.forEach { (date, entries) ->
                    item(key = "day-$date") {
                        Text(
                            text = when (date) {
                                today -> stringResource(R.string.feature_updates_today)
                                today.minusDays(1) -> stringResource(R.string.feature_updates_yesterday)
                                else -> dateFormat.format(Date.from(date.atStartOfDay(zone).toInstant()))
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(entries, key = { it.chapter.id }) { item ->
                        UpdateRow(
                            item = item,
                            download = downloads[item.chapter.id],
                            onOpen = { onOpenReader(item.chapter.mangaId, item.chapter.id) },
                            onOpenManga = { onOpenManga(item.chapter.mangaId) },
                            onDownload = { viewModel.onDownloadClick(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateRow(
    item: UpdateItem,
    download: ChapterDownload?,
    onOpen: () -> Unit,
    onOpenManga: () -> Unit,
    onDownload: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(item.mangaTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(item.chapter.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            CoverImage(
                url = item.mangaCoverUrl,
                modifier = Modifier
                    .width(44.dp)
                    .aspectRatio(COVER_ASPECT_RATIO)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onOpenManga),
            )
        },
        trailingContent = {
            IconButton(onClick = onDownload) {
                when (download?.state) {
                    null -> Icon(Icons.Outlined.Download, stringResource(R.string.feature_updates_download))
                    DownloadState.Done -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    DownloadState.Failed -> Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    else -> CircularProgressIndicator(
                        progress = { if (download.pagesTotal > 0) download.pagesDone.toFloat() / download.pagesTotal else 0f },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        },
        modifier = Modifier
            .alpha(if (item.chapter.read) 0.5f else 1f)
            .clickable(onClick = onOpen),
    )
}

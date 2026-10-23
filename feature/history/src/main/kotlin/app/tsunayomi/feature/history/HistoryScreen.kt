package app.tsunayomi.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tsunayomi.core.designsystem.component.COVER_ASPECT_RATIO
import app.tsunayomi.core.designsystem.component.CoverImage
import app.tsunayomi.core.designsystem.component.EmptyState
import app.tsunayomi.core.model.HistoryItem
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    onOpenManga: (Long) -> Unit,
    onOpenReader: (mangaId: Long, chapterId: Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.openReader.collect { (manga, chapter) -> onOpenReader(manga, chapter) } }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_history_title)) },
                actions = {
                    if (!items.isNullOrEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.feature_history_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val list = items ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.feature_history_empty_title),
                message = stringResource(R.string.feature_history_empty_message),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        val zone = remember { ZoneId.systemDefault() }
        val groups = remember(list) { groupByDay(list, LocalDate.now(zone), zone) }
        val dateFormat = remember { DateFormat.getDateInstance(DateFormat.LONG) }
        val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
        LazyColumn(contentPadding = padding) {
            groups.forEach { (bucket, entries) ->
                item(key = "header-$bucket") {
                    Text(
                        text = when (bucket) {
                            DayBucket.Today -> stringResource(R.string.feature_history_today)
                            DayBucket.Yesterday -> stringResource(R.string.feature_history_yesterday)
                            is DayBucket.Date -> dateFormat.format(Date.from(bucket.date.atStartOfDay(zone).toInstant()))
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                    )
                }
                items(entries, key = { it.mangaId }) { item ->
                    HistoryRow(
                        item = item,
                        source = viewModel.sourceName(item.sourceId)
                            ?: stringResource(R.string.feature_history_local_source),
                        time = timeFormat.format(Date(item.lastReadAt)),
                        onResume = { viewModel.resume(item) },
                        onOpenManga = { onOpenManga(item.mangaId) },
                        onDelete = { viewModel.delete(item) },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.feature_history_clear_title)) },
            text = { Text(stringResource(R.string.feature_history_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearAll()
                }) { Text(stringResource(R.string.feature_history_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.feature_history_cancel)) }
            },
        )
    }
}

@Composable
private fun HistoryRow(
    item: HistoryItem,
    source: String,
    time: String,
    onResume: () -> Unit,
    onOpenManga: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        // The same title can live in several sources (a local copy and a Komga one); the source tells them apart.
        overlineContent = { Text(source, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val progress = if (!item.chapterRead && item.lastPageRead > 0) {
                " · " + stringResource(R.string.feature_history_page, item.lastPageRead + 1)
            } else {
                ""
            }
            Text("${item.chapterName}$progress · $time", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            CoverImage(
                url = item.coverUrl,
                modifier = Modifier
                    .width(44.dp)
                    .aspectRatio(COVER_ASPECT_RATIO)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onOpenManga),
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.feature_history_delete))
            }
        },
        modifier = Modifier.clickable(onClick = onResume),
    )
}

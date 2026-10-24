package app.tsunayomi.feature.details

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tsunayomi.core.designsystem.component.COVER_ASPECT_RATIO
import app.tsunayomi.core.designsystem.component.CoverImage
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.tsunayomi.core.model.Category
import app.tsunayomi.core.model.DefaultCategories
import app.tsunayomi.core.model.ChapterDownload
import app.tsunayomi.core.model.DownloadState
import app.tsunayomi.core.model.Chapter
import app.tsunayomi.core.model.Manga
import app.tsunayomi.core.model.MangaStatus
import app.tsunayomi.source.api.SourceIds
import app.tsunayomi.translation.core.GlossaryEntry
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailsScreen(
    onBack: () -> Unit,
    onRead: (mangaId: Long, chapterId: Long) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var categoryDialog by rememberSaveable { mutableStateOf(false) }
    var languageDialog by rememberSaveable { mutableStateOf(false) }
    var glossaryDialog by rememberSaveable { mutableStateOf(false) }
    val glossary by viewModel.glossary.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    // Ask once for the notification that shows download progress (Android 13+); downloads work either way.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val context = LocalContext.current
    val askNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
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
                actions = {
                    if (viewModel.canDownload) {
                        DownloadsMenu(
                            onDownloadUnread = {
                                askNotificationPermission()
                                viewModel.downloadUnread()
                            },
                            onDeleteAll = viewModel::deleteAllDownloads,
                        )
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
                item {
                    Header(
                        manga = manga,
                        sourceName = state.sourceName,
                        hasCategories = state.categories.isNotEmpty(),
                        onFavoriteClick = {
                            // With categories, adding asks where to put the title; removing is immediate.
                            if (!manga.favorite && state.categories.isNotEmpty()) categoryDialog = true else viewModel.toggleFavorite()
                        },
                        onEditCategories = { categoryDialog = true },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.feature_details_original_lang)) },
                        supportingContent = {
                            Text(manga.originalLang?.let { languageName(it) } ?: stringResource(R.string.feature_details_original_lang_auto))
                        },
                        modifier = Modifier.clickable { languageDialog = true },
                    )
                }
                if (glossary.isNotEmpty()) {
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.feature_details_glossary)) },
                            supportingContent = {
                                Text(pluralStringResource(R.plurals.feature_details_glossary_count, glossary.size, glossary.size))
                            },
                            modifier = Modifier.clickable { glossaryDialog = true },
                        )
                    }
                }
                item {
                    Text(
                        text = pluralStringResource(R.plurals.feature_details_chapter_count, state.chapters.size, state.chapters.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (state.chapters.isEmpty() && !state.refreshing) {
                    item {
                        Text(
                            text = stringResource(
                                if (state.chaptersOnlyExternal) {
                                    R.string.feature_details_chapters_external_only
                                } else {
                                    R.string.feature_details_no_chapters
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                val showLanguage = state.chapters.mapNotNullTo(HashSet()) { it.language }.size > 1
                items(state.chapters, key = { it.id }) { chapter ->
                    ChapterRow(
                        chapter = chapter,
                        showLanguage = showLanguage,
                        download = downloads[chapter.id],
                        canDownload = viewModel.canDownload,
                        onDownloadClick = {
                            if (downloads[chapter.id] == null) askNotificationPermission()
                            viewModel.onDownloadClick(chapter)
                        },
                        onDeleteDownload = { viewModel.deleteDownload(chapter) },
                        onClick = { onRead(manga.id, chapter.id) },
                        onSetRead = { viewModel.setRead(chapter, it) },
                        onReadUpTo = { viewModel.setReadUpTo(chapter) },
                    )
                }
            }
        }
    }

    if (glossaryDialog) {
        GlossaryDialog(
            entries = glossary,
            onDelete = viewModel::deleteGlossaryEntry,
            onClear = {
                glossaryDialog = false
                viewModel.clearGlossary()
            },
            onDismiss = { glossaryDialog = false },
        )
    }

    if (languageDialog) {
        val manga = state.manga
        if (manga != null) {
            LanguageDialog(
                selected = manga.originalLang,
                onSelect = {
                    languageDialog = false
                    viewModel.setOriginalLang(it)
                },
                onDismiss = { languageDialog = false },
            )
        }
    }

    if (categoryDialog) {
        CategoryDialog(
            categories = state.categories,
            initial = state.mangaCategoryIds,
            onConfirm = {
                categoryDialog = false
                viewModel.saveToLibrary(it)
            },
            onDismiss = { categoryDialog = false },
        )
    }
}

/** Names and terms the translator keeps consistent for this title; wrong ones can be removed and are re-learned. */
@Composable
private fun GlossaryDialog(
    entries: List<GlossaryEntry>,
    onDelete: (GlossaryEntry) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val showLanguage = entries.map { it.targetLang }.distinct().size > 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_details_glossary)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.feature_details_glossary_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                entries.forEach { entry ->
                    ListItem(
                        headlineContent = { Text("${entry.source} → ${entry.translation}") },
                        supportingContent = if (showLanguage) {
                            { Text(languageName(entry.targetLang)) }
                        } else {
                            null
                        },
                        trailingContent = {
                            IconButton(onClick = { onDelete(entry) }) {
                                Icon(Icons.Outlined.Delete, stringResource(R.string.feature_details_glossary_delete))
                            }
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_details_ok)) } },
        dismissButton = { TextButton(onClick = onClear) { Text(stringResource(R.string.feature_details_glossary_clear)) } },
    )
}

/** Languages offered for the page text; null means "detect automatically". */
private val ORIGINAL_LANGUAGES = listOf(null, "ja", "ko", "zh", "en", "vi", "th", "id", "es", "pt", "fr", "de", "it", "ru", "uk", "pl", "tr")

@Composable
private fun languageName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(LocalConfiguration.current.locales[0]) }
}

@Composable
private fun LanguageDialog(selected: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_details_original_lang)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.feature_details_original_lang_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ORIGINAL_LANGUAGES.forEach { tag ->
                    ListItem(
                        headlineContent = {
                            Text(tag?.let { languageName(it) } ?: stringResource(R.string.feature_details_original_lang_auto))
                        },
                        leadingContent = { RadioButton(selected = tag == selected, onClick = null) },
                        modifier = Modifier.selectable(selected = tag == selected, role = Role.RadioButton) { onSelect(tag) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_details_cancel)) } },
    )
}

@Composable
private fun CategoryDialog(
    categories: List<Category>,
    initial: Set<Long>,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val effectiveInitial = remember(initial, categories) {
        if (initial.isEmpty()) {
            val readingCategory = categories.firstOrNull { DefaultCategories.isReading(it.name) }
            if (readingCategory != null) setOf(readingCategory.id) else emptySet()
        } else {
            initial
        }
    }
    var selected by remember { mutableStateOf(effectiveInitial) }
    val statusCategoryIds = remember(categories) {
        categories.filter { DefaultCategories.isStatusCategory(it.name) }.map { it.id }.toSet()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_details_categories)) },
        text = {
            Column {
                categories.forEach { category ->
                    val checked = category.id in selected
                    ListItem(
                        headlineContent = { Text(category.name) },
                        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox) { isChecked ->
                            selected = if (isChecked) {
                                if (category.id in statusCategoryIds) {
                                    (selected - statusCategoryIds) + category.id
                                } else {
                                    selected + category.id
                                }
                            } else {
                                selected - category.id
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.feature_details_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_details_cancel)) }
        },
    )
}

@Composable
private fun Header(
    manga: Manga,
    sourceName: String?,
    hasCategories: Boolean,
    onFavoriteClick: () -> Unit,
    onEditCategories: () -> Unit,
) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onFavoriteClick) {
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
            if (manga.favorite && hasCategories) {
                OutlinedButton(onClick = onEditCategories) {
                    Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.feature_details_categories))
                }
            }
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
    showLanguage: Boolean,
    download: ChapterDownload?,
    canDownload: Boolean,
    onDownloadClick: () -> Unit,
    onDeleteDownload: () -> Unit,
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
                    // With several chapter languages listed, tell them apart at a glance.
                    chapter.language?.takeIf { showLanguage }?.uppercase(),
                    chapter.uploadedAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) },
                    chapter.scanlator,
                    if (!chapter.read && chapter.lastPageRead > 0) {
                        stringResource(R.string.feature_details_page_progress, chapter.lastPageRead + 1)
                    } else {
                        null
                    },
                    download?.error?.let { stringResource(R.string.feature_details_download_error, it) },
                )
                if (parts.isNotEmpty()) Text(parts.joinToString(" • "))
            },
            trailingContent = if (canDownload) {
                { DownloadButton(download, onDownloadClick, onDeleteDownload) }
            } else {
                null
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
private fun DownloadButton(download: ChapterDownload?, onClick: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(contentAlignment = Alignment.Center) {
        IconButton(onClick = { if (download?.state == DownloadState.Done) menuOpen = true else onClick() }) {
            when (download?.state) {
                null -> Icon(Icons.Outlined.Download, stringResource(R.string.feature_details_download))
                DownloadState.Queued -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                DownloadState.Downloading, DownloadState.Translating -> {
                    val progress = if (download.pagesTotal > 0) download.pagesDone.toFloat() / download.pagesTotal else 0f
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        // Translating after the download shows in a different colour.
                        color = if (download.state == DownloadState.Translating) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                DownloadState.Done -> Icon(
                    Icons.Filled.CheckCircle,
                    stringResource(R.string.feature_details_downloaded),
                    tint = MaterialTheme.colorScheme.primary,
                )
                DownloadState.Failed -> Icon(
                    Icons.Outlined.ErrorOutline,
                    stringResource(R.string.feature_details_download_retry),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_details_download_delete)) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun DownloadsMenu(onDownloadUnread: () -> Unit, onDeleteAll: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.Download, stringResource(R.string.feature_details_downloads))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_details_download_unread)) },
                onClick = {
                    expanded = false
                    onDownloadUnread()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_details_download_delete_all)) },
                onClick = {
                    expanded = false
                    onDeleteAll()
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

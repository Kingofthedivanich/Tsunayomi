package app.tsunayomi.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tsunayomi.core.model.HistoryItem
import app.tsunayomi.data.LibraryRepository
import app.tsunayomi.data.MangaRepository
import app.tsunayomi.data.ReadingOrder
import app.tsunayomi.data.SourceManager
import app.tsunayomi.source.api.SourceIds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class HistoryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val mangaRepository: MangaRepository,
    private val sourceManager: SourceManager,
) : ViewModel() {
    /** Source name for display; null for the local library, whose label is localized by the screen. */
    fun sourceName(sourceId: Long): String? =
        if (sourceId == SourceIds.LOCAL) null else sourceManager.get(sourceId)?.name

    /** Null while loading, so the empty state does not flash on start. */
    val items: StateFlow<List<HistoryItem>?> = libraryRepository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    private val readerEvents = Channel<Pair<Long, Long>>(Channel.BUFFERED)
    val openReader = readerEvents.receiveAsFlow()

    fun resume(item: HistoryItem) {
        viewModelScope.launch {
            val chapter = ReadingOrder.resumeChapter(mangaRepository.getChapters(item.mangaId), item.chapterId)
            readerEvents.send(item.mangaId to (chapter?.id ?: item.chapterId))
        }
    }

    fun delete(item: HistoryItem) {
        viewModelScope.launch { libraryRepository.deleteHistory(item.mangaId) }
    }

    fun clearAll() {
        viewModelScope.launch { libraryRepository.clearHistory() }
    }
}

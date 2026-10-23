package app.tsunayomi.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.tsunayomi.core.model.Chapter
import app.tsunayomi.core.model.Manga
import app.tsunayomi.data.MangaRepository
import app.tsunayomi.data.ReadingProgressRepository
import app.tsunayomi.data.SourceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class DetailsUiState(
    val manga: Manga? = null,
    val sourceName: String? = null,
    /** Newest first, as most readers list them. */
    val chapters: List<Chapter> = emptyList(),
    val continueChapter: Chapter? = null,
    val hasProgress: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
internal class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mangaRepository: MangaRepository,
    private val progressRepository: ReadingProgressRepository,
    private val sourceManager: SourceManager,
) : ViewModel() {
    private val mangaId = savedStateHandle.toRoute<DetailsRoute>().mangaId
    private val refresh = MutableStateFlow(RefreshState())

    val state: StateFlow<DetailsUiState> = combine(
        mangaRepository.observeManga(mangaId),
        mangaRepository.observeChapters(mangaId),
        refresh,
    ) { manga, chapters, refreshState ->
        // chapters arrive in reading order (source_order ascending).
        val continueChapter = chapters.firstOrNull { !it.read } ?: chapters.firstOrNull()
        DetailsUiState(
            manga = manga,
            sourceName = manga?.let { sourceManager.get(it.sourceId)?.name },
            chapters = chapters.asReversed(),
            continueChapter = continueChapter,
            hasProgress = chapters.any { it.read || it.lastPageRead > 0 },
            refreshing = refreshState.running,
            error = refreshState.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsUiState())

    init {
        viewModelScope.launch {
            if (mangaRepository.needsRefresh(mangaId)) refresh()
        }
    }

    fun refresh() {
        if (refresh.value.running) return
        viewModelScope.launch {
            refresh.value = RefreshState(running = true)
            try {
                mangaRepository.refresh(mangaId)
                refresh.value = RefreshState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                refresh.value = RefreshState(error = e.message ?: e.toString())
            }
        }
    }

    fun dismissError() = refresh.update { it.copy(error = null) }

    fun toggleFavorite() {
        val manga = state.value.manga ?: return
        viewModelScope.launch { mangaRepository.setFavorite(manga.id, !manga.favorite) }
    }

    fun setRead(chapter: Chapter, read: Boolean) {
        viewModelScope.launch { progressRepository.setRead(listOf(chapter.id), read) }
    }

    /** Marks this chapter and every earlier one as read. */
    fun setReadUpTo(chapter: Chapter) {
        val ids = state.value.chapters.filter { it.sourceOrder <= chapter.sourceOrder }.map { it.id }
        viewModelScope.launch { progressRepository.setRead(ids, read = true) }
    }

    private data class RefreshState(val running: Boolean = false, val error: String? = null)
}

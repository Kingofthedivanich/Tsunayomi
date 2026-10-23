package app.tsunayomi.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.tsunayomi.core.model.Category
import app.tsunayomi.core.model.Chapter
import app.tsunayomi.core.model.Manga
import app.tsunayomi.data.LibraryRepository
import app.tsunayomi.data.MangaRepository
import app.tsunayomi.data.ReadingOrder
import app.tsunayomi.data.ReadingProgressRepository
import app.tsunayomi.data.SourceManager
import app.tsunayomi.source.api.ChaptersOnlyExternalException
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
    val categories: List<Category> = emptyList(),
    val mangaCategoryIds: Set<Long> = emptySet(),
    /** Set when the source has chapters, but only on external sites. */
    val chaptersOnlyExternal: Boolean = false,
)

@HiltViewModel
internal class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mangaRepository: MangaRepository,
    private val progressRepository: ReadingProgressRepository,
    private val sourceManager: SourceManager,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {
    private val mangaId = savedStateHandle.toRoute<DetailsRoute>().mangaId
    private val refresh = MutableStateFlow(RefreshState())

    private val categoryState = combine(
        libraryRepository.observeCategories(),
        libraryRepository.observeCategoryIds(mangaId),
    ) { categories, ids -> categories to ids }

    val state: StateFlow<DetailsUiState> = combine(
        mangaRepository.observeManga(mangaId),
        mangaRepository.observeChapters(mangaId),
        refresh,
        categoryState,
    ) { manga, chapters, refreshState, (categories, categoryIds) ->
        // chapters arrive in reading order (source_order ascending).
        val continueChapter = ReadingOrder.continueChapter(chapters)
        DetailsUiState(
            manga = manga,
            sourceName = manga?.let { sourceManager.get(it.sourceId)?.name },
            chapters = chapters.asReversed(),
            continueChapter = continueChapter,
            hasProgress = chapters.any { it.read || it.lastPageRead > 0 },
            refreshing = refreshState.running,
            error = refreshState.error,
            categories = categories,
            mangaCategoryIds = categoryIds,
            chaptersOnlyExternal = refreshState.onlyExternal,
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
            } catch (e: ChaptersOnlyExternalException) {
                refresh.value = RefreshState(onlyExternal = true)
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

    fun setOriginalLang(lang: String?) {
        viewModelScope.launch { mangaRepository.setOriginalLang(mangaId, lang) }
    }

    /** Adds to the library (if needed) with exactly these categories. */
    fun saveToLibrary(categoryIds: Set<Long>) {
        val manga = state.value.manga ?: return
        viewModelScope.launch {
            libraryRepository.setMangaCategories(manga.id, categoryIds)
            if (!manga.favorite) mangaRepository.setFavorite(manga.id, true)
        }
    }

    fun setRead(chapter: Chapter, read: Boolean) {
        viewModelScope.launch { progressRepository.setRead(listOf(chapter.id), read) }
    }

    /** Marks this chapter and every earlier one as read. */
    fun setReadUpTo(chapter: Chapter) {
        val ids = state.value.chapters.filter { it.sourceOrder <= chapter.sourceOrder }.map { it.id }
        viewModelScope.launch { progressRepository.setRead(ids, read = true) }
    }

    private data class RefreshState(
        val running: Boolean = false,
        val error: String? = null,
        val onlyExternal: Boolean = false,
    )
}

package app.tsunayomi.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.tsunayomi.core.model.Chapter
import app.tsunayomi.core.model.Manga
import app.tsunayomi.core.model.ReaderMode
import app.tsunayomi.data.MangaRepository
import app.tsunayomi.data.ReadingProgressRepository
import app.tsunayomi.data.UserSettingsRepository
import app.tsunayomi.source.api.SPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class ReaderUiState(
    val manga: Manga? = null,
    val chapter: Chapter? = null,
    val pages: List<SPage> = emptyList(),
    /** Page to open the chapter at; changes only when a chapter or the mode is (re)loaded. */
    val initialPage: Int = 0,
    val mode: ReaderMode = ReaderMode.PagedRightToLeft,
    val loading: Boolean = true,
    val error: String? = null,
    val previousChapter: Chapter? = null,
    val nextChapter: Chapter? = null,
    val volumeKeys: Boolean = true,
)

@HiltViewModel
internal class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mangaRepository: MangaRepository,
    private val progressRepository: ReadingProgressRepository,
    private val settingsRepository: UserSettingsRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ReaderRoute>()

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    /** Kept apart from [state] so turning a page does not recompose the whole reader. */
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private var chapters: List<Chapter> = emptyList()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val manga = mangaRepository.getManga(route.mangaId)
            if (manga == null) {
                _state.update { it.copy(loading = false, error = "Manga ${route.mangaId} not found") }
                return@launch
            }
            val settings = settingsRepository.settings.first()
            _state.update {
                it.copy(
                    manga = manga,
                    mode = manga.readerMode ?: settings.defaultReaderMode,
                    volumeKeys = settings.volumeKeysNavigation,
                )
            }
            loadChapter(route.chapterId)
        }
    }

    fun loadChapter(chapterId: Long) {
        val manga = _state.value.manga ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                chapters = mangaRepository.getChapters(manga.id)
                val index = chapters.indexOfFirst { it.id == chapterId }
                val chapter = chapters.getOrNull(index) ?: error("Chapter $chapterId not found")
                val pages = mangaRepository.getPages(manga, chapter)
                val startPage = if (!chapter.read && chapter.lastPageRead in pages.indices) chapter.lastPageRead else 0
                _currentPage.value = startPage
                _state.update {
                    it.copy(
                        chapter = chapter,
                        pages = pages,
                        initialPage = startPage,
                        loading = false,
                        previousChapter = chapters.getOrNull(index - 1),
                        nextChapter = chapters.getOrNull(index + 1),
                    )
                }
                progressRepository.saveProgress(chapter.id, startPage, finished = pages.size <= 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    /** [index] equal to the page count means the end-of-chapter screen. */
    fun onPageChanged(index: Int) {
        val state = _state.value
        val chapter = state.chapter ?: return
        if (state.pages.isEmpty()) return
        val page = index.coerceAtMost(state.pages.lastIndex)
        _currentPage.value = page
        val finished = index >= state.pages.lastIndex
        viewModelScope.launch { progressRepository.saveProgress(chapter.id, page, finished) }
    }

    fun openNextChapter() {
        _state.value.nextChapter?.let { loadChapter(it.id) }
    }

    fun openPreviousChapter() {
        _state.value.previousChapter?.let { loadChapter(it.id) }
    }

    fun setMode(mode: ReaderMode) {
        val manga = _state.value.manga ?: return
        _state.update { it.copy(mode = mode, initialPage = _currentPage.value) }
        viewModelScope.launch { mangaRepository.setReaderMode(manga.id, mode) }
    }
}

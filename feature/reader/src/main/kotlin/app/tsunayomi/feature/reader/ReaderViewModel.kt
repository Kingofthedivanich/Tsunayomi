package app.tsunayomi.feature.reader

import android.content.Context
import android.graphics.Bitmap
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
import app.tsunayomi.translation.api.PageRequest
import app.tsunayomi.translation.api.PageTranslation
import app.tsunayomi.translation.api.PageTranslator
import app.tsunayomi.translation.api.TranslationException
import app.tsunayomi.translation.core.TranslationRenderer
import coil3.imageLoader
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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

internal sealed interface PageTranslationState {
    data object Loading : PageTranslationState

    /**
     * [image] is the page with the translation drawn on it. It is rendered only for pages near the reader
     * and dropped for the others: a dozen full-size strips would take hundreds of megabytes.
     */
    data class Done(val translation: PageTranslation, val image: Bitmap? = null) : PageTranslationState

    data class Failed(val error: Throwable) : PageTranslationState
}

@HiltViewModel
internal class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mangaRepository: MangaRepository,
    private val progressRepository: ReadingProgressRepository,
    private val settingsRepository: UserSettingsRepository,
    private val pageTranslator: PageTranslator,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ReaderRoute>()

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    /** Kept apart from [state] so turning a page does not recompose the whole reader. */
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _translationOn = MutableStateFlow(false)
    val translationOn: StateFlow<Boolean> = _translationOn.asStateFlow()

    /** Per page index of the current chapter. */
    private val _translations = MutableStateFlow<Map<Int, PageTranslationState>>(emptyMap())
    val translations: StateFlow<Map<Int, PageTranslationState>> = _translations.asStateFlow()

    // One window at a time: OCR is CPU-heavy and free API tiers limit requests per minute.
    private val translationLimiter = Semaphore(1)
    private val translationJobs = mutableListOf<Job>()
    private val renderJobs = mutableMapOf<Int, Job>()

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
            _translationOn.value = settings.translateByDefault
            loadChapter(route.chapterId)
        }
    }

    fun loadChapter(chapterId: Long) {
        val manga = _state.value.manga ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            resetTranslations()
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
                if (_translationOn.value) translateAround(startPage)
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
        if (_translationOn.value) translateAround(page)
    }

    fun toggleTranslation() {
        val enabled = !_translationOn.value
        _translationOn.value = enabled
        if (enabled) translateAround(_currentPage.value)
    }

    fun retryTranslation(index: Int) {
        _translations.update { states -> states.filterValues { it !is PageTranslationState.Failed } }
        translateAround(index)
    }

    /**
     * Keeps translations ahead of the reader in windows: a short first window so the visible page appears soon,
     * then larger ones whenever fewer than [LOW_WATER] translated pages remain ahead. Each window is one batch,
     * so an LLM sees several pages of context and the request count stays low.
     */
    private fun translateAround(page: Int) {
        updateRenders(page)
        val pageCount = _state.value.pages.size
        val states = _translations.value
        val firstMissing = (page until pageCount).firstOrNull { states[it] == null } ?: return
        val ahead = firstMissing - page
        val size = when {
            ahead == 0 -> FIRST_WINDOW
            ahead < LOW_WATER -> WINDOW
            else -> return
        }
        requestWindow(firstMissing until minOf(firstMissing + size, pageCount))
    }

    private fun requestWindow(range: IntRange) {
        val state = _state.value
        val manga = state.manga ?: return
        val chapter = state.chapter ?: return
        val indices = range.filter { _translations.value[it] == null }
        if (indices.isEmpty()) return

        _translations.update { states -> states + indices.associateWith { PageTranslationState.Loading } }
        translationJobs += viewModelScope.launch {
            try {
                val results = translationLimiter.withPermit {
                    pageTranslator.translate(
                        pages = indices.map { index ->
                            // Remote page URLs can change between sessions (MangaDex@Home), so key by chapter and index.
                            PageRequest("${manga.sourceId}|${chapter.url}|$index") { loadPageBitmap(state.pages[index]) }
                        },
                        sourceLang = manga.originalLang,
                        mangaId = manga.id,
                    )
                }
                _translations.update { states ->
                    states + indices.zip(results).filter { (index, _) -> index in states }
                        .associate { (index, translation) -> index to PageTranslationState.Done(translation) }
                }
                updateRenders(_currentPage.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _translations.update { states -> states + indices.filter { it in states }.associateWith { PageTranslationState.Failed(e) } }
            }
        }
    }

    /** Draws translations for pages around [page] and frees the images of pages far from it. */
    private fun updateRenders(page: Int) {
        val near = (page - RENDER_BEHIND)..(page + RENDER_AHEAD)
        renderJobs.keys.filter { it !in near }.forEach { renderJobs.remove(it)?.cancel() }
        _translations.update { states ->
            states.mapValues { (index, value) ->
                if (value is PageTranslationState.Done && value.image != null && index !in near) value.copy(image = null) else value
            }
        }
        for (index in near) {
            val value = _translations.value[index] as? PageTranslationState.Done ?: continue
            if (value.image != null || index in renderJobs || !TranslationRenderer.needsRendering(value.translation.regions)) continue
            val page = _state.value.pages.getOrNull(index) ?: continue
            renderJobs[index] = viewModelScope.launch {
                try {
                    val bitmap = loadPageBitmap(page)
                    val image = withContext(Dispatchers.Default) { TranslationRenderer.render(bitmap, value.translation.regions) }
                    _translations.update { states ->
                        val current = states[index]
                        if (current is PageTranslationState.Done && current.translation == value.translation) {
                            states + (index to current.copy(image = image))
                        } else {
                            states
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _translations.update { it + (index to PageTranslationState.Failed(e)) }
                } finally {
                    renderJobs.remove(index)
                }
            }
        }
    }

    private suspend fun loadPageBitmap(page: SPage): Bitmap {
        val request = page.toImageRequest(context) {
            size(OCR_MAX_SIZE)
            allowHardware(false)
        }
        val result = context.imageLoader.execute(request)
        return (result as? SuccessResult)?.image?.toBitmap()
            ?: throw TranslationException.Engine("Page image could not be loaded")
    }

    private fun resetTranslations() {
        translationJobs.forEach { it.cancel() }
        translationJobs.clear()
        renderJobs.values.forEach { it.cancel() }
        renderJobs.clear()
        _translations.value = emptyMap()
    }

    fun openNextChapter() {
        _state.value.nextChapter?.let { loadChapter(it.id) }
    }

    fun openPreviousChapter() {
        _state.value.previousChapter?.let { loadChapter(it.id) }
    }

    private companion object {
        /** Pages in the first batch: small, so the visible page shows up quickly. */
        const val FIRST_WINDOW = 5

        /** Pages per later batch. */
        const val WINDOW = 10

        /** Request the next batch when fewer translated pages than this remain ahead. */
        const val LOW_WATER = 4

        const val RENDER_BEHIND = 1
        const val RENDER_AHEAD = 3

        /** Long side for OCR and the rendered translation; enough for small text, bounded in memory. */
        const val OCR_MAX_SIZE = 2048
    }

    fun setMode(mode: ReaderMode) {
        val manga = _state.value.manga ?: return
        _state.update { it.copy(mode = mode, initialPage = _currentPage.value) }
        viewModelScope.launch { mangaRepository.setReaderMode(manga.id, mode) }
    }
}

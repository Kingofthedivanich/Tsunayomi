package app.tsunayomi.feature.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.tsunayomi.data.MangaRepository
import app.tsunayomi.data.SourceManager
import app.tsunayomi.source.api.CatalogSource
import app.tsunayomi.source.api.FilterList
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SourceNotConfiguredException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal sealed interface SourceResult {
    data object Loading : SourceResult
    data class Success(val items: List<SManga>) : SourceResult
    data class Failure(val message: String) : SourceResult

    /** The source can't search by the chosen mode; such sources are listed apart, not as results. */
    data object Unsupported : SourceResult
}

/** What the global search matches the text against. */
internal enum class GlobalSearchMode { Title, Tag, Author }

internal data class GlobalSearchUiState(
    val query: String = "",
    val mode: GlobalSearchMode = GlobalSearchMode.Title,
    /** Sources in display order; unconfigured ones are dropped instead of showing an error. */
    val results: List<Pair<CatalogSource, SourceResult>> = emptyList(),
    /** Sources that can't search by [mode], listed apart instead of as empty results. */
    val unsupported: List<CatalogSource> = emptyList(),
)

@HiltViewModel
internal class GlobalSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sourceManager: SourceManager,
    private val mangaRepository: MangaRepository,
) : ViewModel() {
    private val route = runCatching { savedStateHandle.toRoute<GlobalSearchRoute>() }.getOrNull()

    private val _state = MutableStateFlow(
        GlobalSearchUiState(
            mode = route?.mode?.let { mode -> GlobalSearchMode.entries.firstOrNull { it.name == mode } } ?: GlobalSearchMode.Title,
        ),
    )
    val state: StateFlow<GlobalSearchUiState> = _state.asStateFlow()

    private val openManga = Channel<Long>(Channel.BUFFERED)
    val openMangaEvents = openManga.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        route?.query?.takeIf { it.isNotBlank() }?.let(::search)
    }

    /** Switches what the text is matched against and searches [query] again that way. */
    fun setMode(mode: GlobalSearchMode, query: String) {
        _state.update { it.copy(mode = mode) }
        search(query)
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchJob?.cancel()
        val mode = _state.value.mode
        // currentCatalogSources() waits for extensions to load, so it runs off the main thread with the search.
        _state.update { it.copy(query = trimmed, results = emptyList(), unsupported = emptyList()) }
        searchJob = viewModelScope.launch {
            val sources = withContext(Dispatchers.IO) { sourceManager.currentCatalogSources() }
            _state.update { it.copy(results = sources.map { source -> source to SourceResult.Loading }) }
            val limiter = Semaphore(PARALLEL_SOURCES)
            sources.forEach { source ->
                launch {
                    val result = limiter.withPermit {
                        try {
                            val page = when (mode) {
                                GlobalSearchMode.Title -> source.search(trimmed, FilterList(), page = 1)
                                GlobalSearchMode.Tag -> source.searchByTags(setOf(trimmed), page = 1)
                                GlobalSearchMode.Author -> source.searchByAuthor(trimmed, page = 1)
                            }
                            page?.let { SourceResult.Success(it.items) } ?: SourceResult.Unsupported
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: SourceNotConfiguredException) {
                            null
                        } catch (e: Exception) {
                            SourceResult.Failure(e.message ?: e.toString())
                        }
                    }
                    _state.update { current ->
                        when (result) {
                            null -> current.copy(results = current.results.filterNot { it.first.id == source.id })
                            SourceResult.Unsupported -> current.copy(
                                results = current.results.filterNot { it.first.id == source.id },
                                unsupported = current.unsupported + source,
                            )
                            else -> current.copy(
                                results = current.results.map { if (it.first.id == source.id) source to result else it },
                            )
                        }
                    }
                }
            }
        }
    }


    fun onMangaClick(source: CatalogSource, manga: SManga) {
        viewModelScope.launch { openManga.send(mangaRepository.getOrCreate(source.id, manga)) }
    }

    private companion object {
        const val PARALLEL_SOURCES = 4
    }
}

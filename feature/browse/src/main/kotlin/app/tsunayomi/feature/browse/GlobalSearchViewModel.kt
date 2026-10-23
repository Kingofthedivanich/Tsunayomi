package app.tsunayomi.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tsunayomi.data.MangaRepository
import app.tsunayomi.data.SourceManager
import app.tsunayomi.source.api.CatalogSource
import app.tsunayomi.source.api.FilterList
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SourceNotConfiguredException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import javax.inject.Inject

internal sealed interface SourceResult {
    data object Loading : SourceResult
    data class Success(val items: List<SManga>) : SourceResult
    data class Failure(val message: String) : SourceResult
}

internal data class GlobalSearchUiState(
    val query: String = "",
    /** Sources in display order; unconfigured ones are dropped instead of showing an error. */
    val results: List<Pair<CatalogSource, SourceResult>> = emptyList(),
)

@HiltViewModel
internal class GlobalSearchViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val mangaRepository: MangaRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GlobalSearchUiState())
    val state: StateFlow<GlobalSearchUiState> = _state.asStateFlow()

    private val openManga = Channel<Long>(Channel.BUFFERED)
    val openMangaEvents = openManga.receiveAsFlow()

    private var searchJob: Job? = null

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchJob?.cancel()
        val sources = sourceManager.catalogSources
        _state.value = GlobalSearchUiState(trimmed, sources.map { it to SourceResult.Loading })
        searchJob = viewModelScope.launch {
            val limiter = Semaphore(PARALLEL_SOURCES)
            sources.forEach { source ->
                launch {
                    val result = limiter.withPermit {
                        try {
                            SourceResult.Success(source.search(trimmed, FilterList(), page = 1).items)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: SourceNotConfiguredException) {
                            null
                        } catch (e: Exception) {
                            SourceResult.Failure(e.message ?: e.toString())
                        }
                    }
                    _state.update { current ->
                        current.copy(
                            results = if (result == null) {
                                current.results.filterNot { it.first.id == source.id }
                            } else {
                                current.results.map { if (it.first.id == source.id) source to result else it }
                            },
                        )
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

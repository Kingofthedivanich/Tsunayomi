package app.tsunayomi.feature.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.tsunayomi.data.MangaRepository
import app.tsunayomi.data.SourceManager
import app.tsunayomi.data.UserSettingsRepository
import app.tsunayomi.source.api.CatalogSource
import app.tsunayomi.source.api.FilterList
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SourceNotConfiguredException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal enum class CatalogListing { Popular, Latest }

internal data class CatalogUiState(
    val items: List<SManga> = emptyList(),
    val listing: CatalogListing = CatalogListing.Popular,
    /** Non-null while a search is active. */
    val query: String? = null,
    val loading: Boolean = false,
    val hasNext: Boolean = true,
    val notConfigured: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
internal class CatalogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    sourceManager: SourceManager,
    private val mangaRepository: MangaRepository,
    private val settingsRepository: UserSettingsRepository,
) : ViewModel() {
    val source: CatalogSource = sourceManager.getCatalog(savedStateHandle.toRoute<CatalogRoute>().sourceId)

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private val openManga = Channel<Long>(Channel.BUFFERED)
    val openMangaEvents = openManga.receiveAsFlow()

    private var nextPage = 1
    private var loadJob: Job? = null

    init {
        reload()
    }

    fun setListing(listing: CatalogListing) {
        _state.update { it.copy(listing = listing, query = null) }
        reload()
    }

    fun search(query: String) {
        _state.update { it.copy(query = query.trim().ifEmpty { null }) }
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        nextPage = 1
        _state.update { it.copy(items = emptyList(), hasNext = true, error = null, notConfigured = false) }
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading && loadJob?.isActive == true || !current.hasNext) return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val page = nextPage
            try {
                val result = when {
                    current.query != null -> source.search(current.query, FilterList(), page)
                    current.listing == CatalogListing.Latest && source.supportsLatest -> source.getLatest(page)
                    else -> source.getPopular(page)
                }
                nextPage = page + 1
                _state.update {
                    it.copy(items = (it.items + result.items).distinctBy(SManga::url), hasNext = result.hasNext, loading = false)
                }
            } catch (e: SourceNotConfiguredException) {
                _state.update { it.copy(loading = false, hasNext = false, notConfigured = true) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun onMangaClick(manga: SManga) {
        viewModelScope.launch {
            openManga.send(mangaRepository.getOrCreate(source.id, manga))
        }
    }

    fun setLibraryFolder(uri: String) {
        viewModelScope.launch {
            settingsRepository.setLocalLibraryUri(uri)
            reload()
        }
    }
}

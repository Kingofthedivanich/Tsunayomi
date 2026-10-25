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
import eu.kanade.tachiyomi.network.isCloudflareChallenge
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

/** Tags or an author the catalog lists titles for: tapped on a title's page or picked in the tag sheet. */
internal sealed interface CatalogLookup {
    data class Tags(val names: Set<String>) : CatalogLookup

    data class Author(val name: String) : CatalogLookup
}

/** The source's tags for the tag sheet. */
internal sealed interface TagChoices {
    data object NotLoaded : TagChoices

    data object Loading : TagChoices

    data class Loaded(val tags: List<String>) : TagChoices

    /** The source can't search by tags, or loading them failed with [error]. */
    data class Unavailable(val error: String? = null) : TagChoices
}

internal data class CatalogUiState(
    val items: List<SManga> = emptyList(),
    val listing: CatalogListing = CatalogListing.Popular,
    /** Non-null while a search is active. */
    val query: String? = null,
    val loading: Boolean = false,
    val hasNext: Boolean = true,
    val notConfigured: Boolean = false,
    val error: String? = null,
    val isCloudflareError: Boolean = false,
    /** True once the user applied filters; listing chips then no longer apply. */
    val filtersApplied: Boolean = false,
    val filterVersion: Int = 0,
    val lookup: CatalogLookup? = null,
    /** The source can't search by [lookup]. */
    val lookupUnsupported: Boolean = false,
)

@HiltViewModel
internal class CatalogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    sourceManager: SourceManager,
    private val mangaRepository: MangaRepository,
    private val settingsRepository: UserSettingsRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<CatalogRoute>()
    val source: CatalogSource = sourceManager.getCatalog(route.sourceId)

    private val _state = MutableStateFlow(
        CatalogUiState(
            lookup = route.tag?.let { CatalogLookup.Tags(setOf(it)) } ?: route.author?.let(CatalogLookup::Author),
        ),
    )
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private val openManga = Channel<Long>(Channel.BUFFERED)
    val openMangaEvents = openManga.receiveAsFlow()

    /** Mutable filter objects edited by the sheet; [CatalogUiState.filterVersion] tracks edits. */
    var filters: FilterList = source.getFilters()
        private set
    val hasFilters: Boolean get() = filters.isNotEmpty()

    private var nextPage = 1
    private var loadJob: Job? = null

    init {
        reload()
        viewModelScope.launch {
            savedStateHandle.getStateFlow("reload_requested", false).collect { requested ->
                if (requested) {
                    savedStateHandle["reload_requested"] = false
                    reload()
                }
            }
        }
    }

    fun setListing(listing: CatalogListing) {
        filters = source.getFilters()
        _state.update {
            it.copy(listing = listing, query = null, filtersApplied = false, filterVersion = it.filterVersion + 1, lookup = null)
        }
        reload()
    }

    fun editFilters(edit: () -> Unit) {
        edit()
        _state.update { it.copy(filterVersion = it.filterVersion + 1) }
    }

    fun resetFilters() {
        filters = source.getFilters()
        _state.update { it.copy(filterVersion = it.filterVersion + 1) }
    }

    fun applyFilters() {
        _state.update { it.copy(filtersApplied = true, lookup = null) }
        reload()
    }

    fun search(query: String) {
        _state.update { it.copy(query = query.trim().ifEmpty { null }, lookup = null) }
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        nextPage = 1
        _state.update {
            it.copy(
                items = emptyList(),
                hasNext = true,
                error = null,
                notConfigured = false,
                isCloudflareError = false,
                lookupUnsupported = false,
            )
        }
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading && loadJob?.isActive == true || !current.hasNext) return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, isCloudflareError = false) }
            val page = nextPage
            try {
                val result = when {
                    current.lookup != null -> {
                        val found = when (current.lookup) {
                            is CatalogLookup.Tags -> source.searchByTags(current.lookup.names, page)
                            is CatalogLookup.Author -> source.searchByAuthor(current.lookup.name, page)
                        }
                        if (found == null) {
                            _state.update { it.copy(loading = false, hasNext = false, lookupUnsupported = true) }
                            return@launch
                        }
                        found
                    }
                    current.query != null || current.filtersApplied -> source.search(current.query.orEmpty(), filters, page)
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
                val isCf = e.isCloudflareChallenge
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: e.toString(),
                        isCloudflareError = isCf,
                    )
                }
            }
        }
    }

    private val _tagChoices = MutableStateFlow<TagChoices>(TagChoices.NotLoaded)
    val tagChoices: StateFlow<TagChoices> = _tagChoices.asStateFlow()

    /** Loads the source's tags once, when the tag sheet is first opened. */
    fun loadTags() {
        if (_tagChoices.value is TagChoices.Loading || _tagChoices.value is TagChoices.Loaded) return
        _tagChoices.value = TagChoices.Loading
        viewModelScope.launch {
            _tagChoices.value = try {
                source.searchableTags()?.let { TagChoices.Loaded(it) } ?: TagChoices.Unavailable()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                TagChoices.Unavailable(e.message ?: e.toString())
            }
        }
    }

    /** Lists titles that have all of [tags]; an empty set goes back to the plain catalog. */
    fun searchTags(tags: Set<String>) {
        _state.update { it.copy(query = null, filtersApplied = false, lookup = tags.takeIf { t -> t.isNotEmpty() }?.let(CatalogLookup::Tags)) }
        reload()
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

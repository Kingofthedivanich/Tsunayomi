package app.tsunayomi.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tsunayomi.core.model.LibraryManga
import app.tsunayomi.core.model.LibrarySort
import app.tsunayomi.data.LibraryRepository
import app.tsunayomi.data.LibrarySorting
import app.tsunayomi.data.MangaRepository
import app.tsunayomi.data.ReadingOrder
import app.tsunayomi.data.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One library tab. A null [categoryId] is the tab of entries without a category. */
internal data class LibraryTab(
    val categoryId: Long?,
    val name: String?,
    val items: List<LibraryManga>,
)

internal data class LibraryUiState(
    val loaded: Boolean = false,
    val tabs: List<LibraryTab> = emptyList(),
    val totalCount: Int = 0,
    val sort: LibrarySort = LibrarySort.Title,
    val ascending: Boolean = true,
    val query: String = "",
)

internal sealed interface LibraryEvent {
    data class OpenReader(val mangaId: Long, val chapterId: Long) : LibraryEvent
    data class OpenManga(val mangaId: Long) : LibraryEvent
}

@HiltViewModel
internal class LibraryViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    private val mangaRepository: MangaRepository,
    private val settingsRepository: UserSettingsRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val events = Channel<LibraryEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val state: StateFlow<LibraryUiState> = combine(
        libraryRepository.observeLibrary(),
        libraryRepository.observeCategories(),
        settingsRepository.settings,
        query,
    ) { library, categories, settings, query ->
        val visible = LibrarySorting.sort(
            LibrarySorting.filter(library, query),
            settings.librarySort,
            settings.librarySortAscending,
        )
        val tabs = if (categories.isEmpty()) {
            listOf(LibraryTab(categoryId = null, name = null, items = visible))
        } else {
            val known = categories.mapTo(HashSet()) { it.id }
            // Entries whose categories were all deleted land in the default tab too.
            val uncategorized = visible.filter { item -> item.categoryIds.none { it in known } }
            val defaultTab = LibraryTab(null, null, uncategorized).takeIf { uncategorized.isNotEmpty() }
            listOfNotNull(defaultTab) + categories.map { category ->
                LibraryTab(category.id, category.name, visible.filter { category.id in it.categoryIds })
            }
        }
        LibraryUiState(
            loaded = true,
            tabs = tabs,
            totalCount = library.size,
            sort = settings.librarySort,
            ascending = settings.librarySortAscending,
            query = query,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    /** Selecting the active sort flips its direction, like column headers in a table. */
    fun setSort(sort: LibrarySort) {
        val current = state.value
        val ascending = if (current.sort == sort) !current.ascending else sort == LibrarySort.Title
        viewModelScope.launch { settingsRepository.setLibrarySort(sort, ascending) }
    }

    fun continueReading(mangaId: Long) {
        viewModelScope.launch {
            val chapter = ReadingOrder.continueChapter(mangaRepository.getChapters(mangaId))
            events.send(
                if (chapter != null) LibraryEvent.OpenReader(mangaId, chapter.id) else LibraryEvent.OpenManga(mangaId),
            )
        }
    }
}

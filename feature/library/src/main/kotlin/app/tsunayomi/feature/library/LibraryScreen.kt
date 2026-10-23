package app.tsunayomi.feature.library

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.tsunayomi.core.designsystem.component.EmptyState
import app.tsunayomi.core.designsystem.component.MangaGrid
import app.tsunayomi.core.designsystem.component.MangaGridItem
import app.tsunayomi.data.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data object LibraryRoute

fun NavGraphBuilder.libraryScreen(onOpenManga: (Long) -> Unit) {
    composable<LibraryRoute> { LibraryScreen(onOpenManga) }
}

@HiltViewModel
internal class LibraryViewModel @Inject constructor(repository: MangaRepository) : ViewModel() {
    /** Null while loading, so the empty state does not flash on start. */
    val items: StateFlow<List<MangaGridItem>?> = repository.observeFavorites()
        .map { list -> list.map { MangaGridItem(it.id.toString(), it.title, it.coverUrl) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(
    onOpenManga: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_library_title)) }) },
    ) { padding ->
        val list = items ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.CollectionsBookmark,
                title = stringResource(R.string.feature_library_empty_title),
                message = stringResource(R.string.feature_library_empty_message),
                modifier = Modifier.padding(padding),
            )
        } else {
            MangaGrid(
                items = list,
                onClick = { onOpenManga(it.key.toLong()) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

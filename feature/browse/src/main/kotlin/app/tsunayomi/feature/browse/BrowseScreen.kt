package app.tsunayomi.feature.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import app.tsunayomi.core.designsystem.component.EmptyState
import app.tsunayomi.data.SourceManager
import app.tsunayomi.source.api.CatalogSource
import app.tsunayomi.source.api.SourceIds
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class BrowseViewModel @Inject constructor(sourceManager: SourceManager) : ViewModel() {
    val sources: StateFlow<List<CatalogSource>?> = sourceManager.catalogSources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseScreen(
    onOpenCatalog: (Long) -> Unit,
    onOpenGlobalSearch: () -> Unit,
    onOpenExtensions: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val sources = viewModel.sources.collectAsStateWithLifecycle().value ?: return
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_browse_title)) },
                actions = {
                    IconButton(onClick = onOpenExtensions) {
                        Icon(Icons.Outlined.Extension, stringResource(R.string.feature_browse_extensions))
                    }
                    IconButton(onClick = onOpenGlobalSearch) {
                        Icon(Icons.Outlined.TravelExplore, stringResource(R.string.feature_browse_global_search))
                    }
                },
            )
        },
    ) { padding ->
        if (sources.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Explore,
                title = stringResource(R.string.feature_browse_empty_title),
                message = stringResource(R.string.feature_browse_empty_message),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(contentPadding = padding) {
            items(sources, key = { it.id }) { source ->
                ListItem(
                    headlineContent = { Text(source.displayName()) },
                    supportingContent = {
                        Text(
                            when (source.id) {
                                SourceIds.LOCAL -> stringResource(R.string.feature_browse_local_source_summary)
                                SourceIds.KOMGA -> stringResource(R.string.feature_browse_komga_summary)
                                else -> source.languageLabel() ?: stringResource(R.string.feature_browse_multi_language_summary)
                            },
                        )
                    },
                    leadingContent = {
                        val icon = source.iconUri
                        if (icon != null) {
                            AsyncImage(model = icon, contentDescription = null, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                when (source.id) {
                                    SourceIds.LOCAL -> Icons.Outlined.Folder
                                    SourceIds.KOMGA -> Icons.Outlined.Dns
                                    else -> Icons.Outlined.Public
                                },
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier.clickable { onOpenCatalog(source.id) },
                )
            }
        }
    }
}

package app.tsunayomi.feature.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
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
import app.tsunayomi.core.designsystem.component.EmptyState
import app.tsunayomi.data.SourceManager
import app.tsunayomi.source.api.CatalogSource
import app.tsunayomi.source.api.SourceIds
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class BrowseViewModel @Inject constructor(sourceManager: SourceManager) : ViewModel() {
    val sources: List<CatalogSource> = sourceManager.catalogSources
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseScreen(
    onOpenCatalog: (Long) -> Unit,
    onOpenGlobalSearch: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_browse_title)) },
                actions = {
                    IconButton(onClick = onOpenGlobalSearch) {
                        Icon(Icons.Outlined.TravelExplore, stringResource(R.string.feature_browse_global_search))
                    }
                },
            )
        },
    ) { padding ->
        if (viewModel.sources.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Explore,
                title = stringResource(R.string.feature_browse_empty_title),
                message = stringResource(R.string.feature_browse_empty_message),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(contentPadding = padding) {
            items(viewModel.sources, key = { it.id }) { source ->
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
                        Icon(
                            when (source.id) {
                                SourceIds.LOCAL -> Icons.Outlined.Folder
                                SourceIds.KOMGA -> Icons.Outlined.Dns
                                else -> Icons.Outlined.Public
                            },
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable { onOpenCatalog(source.id) },
                )
            }
        }
    }
}

package app.tsunayomi.feature.library

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.tsunayomi.core.designsystem.component.EmptyState
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

fun NavGraphBuilder.libraryScreen() {
    composable<LibraryRoute> { LibraryScreen() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_library_title)) }) },
    ) { padding ->
        EmptyState(
            icon = Icons.Outlined.CollectionsBookmark,
            title = stringResource(R.string.feature_library_empty_title),
            message = stringResource(R.string.feature_library_empty_message),
            modifier = Modifier.padding(padding),
        )
    }
}

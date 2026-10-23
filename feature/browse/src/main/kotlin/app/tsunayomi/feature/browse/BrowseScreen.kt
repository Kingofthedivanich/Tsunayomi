package app.tsunayomi.feature.browse

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
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
data object BrowseRoute

fun NavGraphBuilder.browseScreen() {
    composable<BrowseRoute> { BrowseScreen() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_browse_title)) }) },
    ) { padding ->
        EmptyState(
            icon = Icons.Outlined.Explore,
            title = stringResource(R.string.feature_browse_empty_title),
            message = stringResource(R.string.feature_browse_empty_message),
            modifier = Modifier.padding(padding),
        )
    }
}

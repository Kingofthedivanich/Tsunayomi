package app.tsunayomi.feature.history

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
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
data object HistoryRoute

fun NavGraphBuilder.historyScreen() {
    composable<HistoryRoute> { HistoryScreen() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_history_title)) }) },
    ) { padding ->
        EmptyState(
            icon = Icons.Outlined.History,
            title = stringResource(R.string.feature_history_empty_title),
            message = stringResource(R.string.feature_history_empty_message),
            modifier = Modifier.padding(padding),
        )
    }
}

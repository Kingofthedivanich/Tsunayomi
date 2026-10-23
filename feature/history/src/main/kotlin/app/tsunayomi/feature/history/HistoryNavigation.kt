package app.tsunayomi.feature.history

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HistoryRoute

fun NavGraphBuilder.historyScreen(
    onOpenManga: (mangaId: Long) -> Unit,
    onOpenReader: (mangaId: Long, chapterId: Long) -> Unit,
) {
    composable<HistoryRoute> { HistoryScreen(onOpenManga = onOpenManga, onOpenReader = onOpenReader) }
}

package app.tsunayomi.feature.reader

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data class ReaderRoute(val mangaId: Long, val chapterId: Long)

fun NavGraphBuilder.readerScreen(onBack: () -> Unit) {
    composable<ReaderRoute> { ReaderScreen(onBack = onBack) }
}

fun NavController.navigateToReader(mangaId: Long, chapterId: Long) = navigate(ReaderRoute(mangaId, chapterId))

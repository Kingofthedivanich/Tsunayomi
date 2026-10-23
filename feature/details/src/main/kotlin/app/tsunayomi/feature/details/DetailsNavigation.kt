package app.tsunayomi.feature.details

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data class DetailsRoute(val mangaId: Long)

fun NavGraphBuilder.detailsScreen(
    onBack: () -> Unit,
    onRead: (mangaId: Long, chapterId: Long) -> Unit,
) {
    composable<DetailsRoute> { DetailsScreen(onBack = onBack, onRead = onRead) }
}

fun NavController.navigateToDetails(mangaId: Long) = navigate(DetailsRoute(mangaId))

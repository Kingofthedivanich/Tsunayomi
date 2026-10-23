package app.tsunayomi.feature.browse

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object BrowseRoute

@Serializable
data class CatalogRoute(val sourceId: Long)

fun NavGraphBuilder.browseScreen(onOpenCatalog: (sourceId: Long) -> Unit) {
    composable<BrowseRoute> { BrowseScreen(onOpenCatalog = onOpenCatalog) }
}

fun NavGraphBuilder.catalogScreen(onBack: () -> Unit, onOpenManga: (mangaId: Long) -> Unit) {
    composable<CatalogRoute> { CatalogScreen(onBack = onBack, onOpenManga = onOpenManga) }
}

fun NavController.navigateToCatalog(sourceId: Long) = navigate(CatalogRoute(sourceId))

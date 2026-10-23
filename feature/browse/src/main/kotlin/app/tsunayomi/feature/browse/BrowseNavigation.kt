package app.tsunayomi.feature.browse

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object BrowseRoute

@Serializable
data class CatalogRoute(val sourceId: Long)

@Serializable
data object GlobalSearchRoute

fun NavGraphBuilder.browseScreen(onOpenCatalog: (sourceId: Long) -> Unit, onOpenGlobalSearch: () -> Unit) {
    composable<BrowseRoute> { BrowseScreen(onOpenCatalog = onOpenCatalog, onOpenGlobalSearch = onOpenGlobalSearch) }
}

fun NavGraphBuilder.catalogScreen(
    onBack: () -> Unit,
    onOpenManga: (mangaId: Long) -> Unit,
    onOpenSourceSettings: (sourceId: Long) -> Unit,
) {
    composable<CatalogRoute> {
        CatalogScreen(onBack = onBack, onOpenManga = onOpenManga, onOpenSourceSettings = onOpenSourceSettings)
    }
}

fun NavGraphBuilder.globalSearchScreen(onBack: () -> Unit, onOpenManga: (mangaId: Long) -> Unit) {
    composable<GlobalSearchRoute> { GlobalSearchScreen(onBack = onBack, onOpenManga = onOpenManga) }
}

fun NavController.navigateToCatalog(sourceId: Long) = navigate(CatalogRoute(sourceId))

fun NavController.navigateToGlobalSearch() = navigate(GlobalSearchRoute)

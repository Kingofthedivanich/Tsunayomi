package app.tsunayomi.feature.browse

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data object BrowseRoute

@Serializable
data class CatalogRoute(val sourceId: Long)

@Serializable
data object GlobalSearchRoute

@Serializable
data class WebViewRoute(val url: String, val sourceId: Long)

fun NavGraphBuilder.browseScreen(
    onOpenCatalog: (sourceId: Long) -> Unit,
    onOpenGlobalSearch: () -> Unit,
    onOpenExtensions: () -> Unit,
) {
    composable<BrowseRoute> {
        BrowseScreen(onOpenCatalog = onOpenCatalog, onOpenGlobalSearch = onOpenGlobalSearch, onOpenExtensions = onOpenExtensions)
    }
}

fun NavGraphBuilder.catalogScreen(
    onBack: () -> Unit,
    onOpenManga: (mangaId: Long) -> Unit,
    onOpenSourceSettings: (sourceId: Long) -> Unit,
    onOpenWebView: (url: String, sourceId: Long) -> Unit,
) {
    composable<CatalogRoute> {
        CatalogScreen(
            onBack = onBack,
            onOpenManga = onOpenManga,
            onOpenSourceSettings = onOpenSourceSettings,
            onOpenWebView = onOpenWebView,
        )
    }
}

fun NavGraphBuilder.webViewScreen(
    onClose: () -> Unit,
) {
    composable<WebViewRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<WebViewRoute>()
        WebViewScreen(
            url = route.url,
            sourceId = route.sourceId,
            onClose = onClose,
        )
    }
}

fun NavGraphBuilder.globalSearchScreen(onBack: () -> Unit, onOpenManga: (mangaId: Long) -> Unit) {
    composable<GlobalSearchRoute> { GlobalSearchScreen(onBack = onBack, onOpenManga = onOpenManga) }
}

fun NavController.navigateToCatalog(sourceId: Long) = navigate(CatalogRoute(sourceId))

fun NavController.navigateToGlobalSearch() = navigate(GlobalSearchRoute)

fun NavController.navigateToWebView(url: String, sourceId: Long) = navigate(WebViewRoute(url, sourceId))

package app.tsunayomi.feature.browse

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data object BrowseRoute

@Serializable
data class CatalogRoute(val sourceId: Long, val tag: String? = null, val author: String? = null)

/** [mode] is a [GlobalSearchMode] name; with a [query] the search starts right away. */
@Serializable
data class GlobalSearchRoute(val query: String = "", val mode: String = GlobalSearchMode.Title.name)

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

/** The source's titles with [tag] or by [author], from tapping one on a title's page. */
fun NavController.navigateToCatalogLookup(sourceId: Long, tag: String? = null, author: String? = null) =
    navigate(CatalogRoute(sourceId, tag = tag, author = author))

fun NavController.navigateToGlobalSearch() = navigate(GlobalSearchRoute())

/** Every source's titles with [tag] or by [author], from tapping one on a title's page. */
fun NavController.navigateToGlobalLookup(tag: String? = null, author: String? = null) = navigate(
    if (tag != null) {
        GlobalSearchRoute(query = tag, mode = GlobalSearchMode.Tag.name)
    } else {
        GlobalSearchRoute(query = author.orEmpty(), mode = GlobalSearchMode.Author.name)
    },
)

fun NavController.navigateToWebView(url: String, sourceId: Long) = navigate(WebViewRoute(url, sourceId))

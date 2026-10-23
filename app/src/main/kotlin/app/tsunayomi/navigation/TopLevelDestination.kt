package app.tsunayomi.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.ui.graphics.vector.ImageVector
import app.tsunayomi.R
import app.tsunayomi.feature.browse.BrowseRoute
import app.tsunayomi.feature.history.HistoryRoute
import app.tsunayomi.feature.library.LibraryRoute
import app.tsunayomi.feature.settings.MoreGraph
import app.tsunayomi.feature.updates.UpdatesRoute
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Library(LibraryRoute, LibraryRoute::class, R.string.nav_library, Icons.Filled.CollectionsBookmark, Icons.Outlined.CollectionsBookmark),
    Updates(UpdatesRoute, UpdatesRoute::class, R.string.nav_updates, Icons.Filled.NewReleases, Icons.Outlined.NewReleases),
    Browse(BrowseRoute, BrowseRoute::class, R.string.nav_browse, Icons.Filled.Explore, Icons.Outlined.Explore),
    History(HistoryRoute, HistoryRoute::class, R.string.nav_history, Icons.Filled.History, Icons.Outlined.History),
    More(MoreGraph, MoreGraph::class, R.string.nav_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz),
}

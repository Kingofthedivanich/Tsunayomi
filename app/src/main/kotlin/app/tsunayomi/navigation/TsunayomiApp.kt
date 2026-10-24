package app.tsunayomi.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.tsunayomi.feature.browse.BrowseRoute
import app.tsunayomi.feature.browse.browseScreen
import app.tsunayomi.feature.extensions.extensionsScreen
import app.tsunayomi.feature.extensions.navigateToExtensionSourceSettings
import app.tsunayomi.feature.extensions.navigateToExtensions
import app.tsunayomi.feature.browse.catalogScreen
import app.tsunayomi.feature.browse.globalSearchScreen
import app.tsunayomi.feature.browse.navigateToCatalog
import app.tsunayomi.feature.browse.navigateToGlobalSearch
import app.tsunayomi.feature.browse.navigateToWebView
import app.tsunayomi.feature.browse.webViewScreen
import app.tsunayomi.feature.details.detailsScreen
import app.tsunayomi.feature.details.navigateToDetails
import app.tsunayomi.feature.history.HistoryRoute
import app.tsunayomi.feature.history.historyScreen
import app.tsunayomi.feature.library.LibraryRoute
import app.tsunayomi.feature.library.categoriesScreen
import app.tsunayomi.feature.library.libraryScreen
import app.tsunayomi.feature.library.navigateToCategories
import app.tsunayomi.feature.reader.navigateToReader
import app.tsunayomi.feature.reader.readerScreen
import app.tsunayomi.feature.settings.komgaSettingsScreen
import app.tsunayomi.feature.settings.moreGraph
import app.tsunayomi.feature.updates.UpdatesRoute
import app.tsunayomi.feature.updates.updatesScreen
import app.tsunayomi.feature.settings.navigateToKomgaSettings
import app.tsunayomi.source.api.SourceIds

/** Root of the UI: adaptive navigation (bottom bar on phones, rail on tablets) around the nav host. */
@Composable
fun TsunayomiApp(
    navController: NavHostController = rememberNavController(),
    openUpdates: Boolean = false,
    onUpdatesOpened: () -> Unit = {},
) {
    // Opened from the "new chapters" notification.
    LaunchedEffect(openUpdates) {
        if (openUpdates) {
            navController.navigateToTopLevel(TopLevelDestination.Updates)
            onUpdatesOpened()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showNavigation = currentDestination?.isTopLevel() ?: true

    NavigationSuiteScaffold(
        layoutType = if (showNavigation) {
            NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
        } else {
            NavigationSuiteType.None
        },
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
                item(
                    selected = selected,
                    onClick = { navController.navigateToTopLevel(destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = LibraryRoute) {
            libraryScreen(
                onOpenManga = navController::navigateToDetails,
                onOpenReader = navController::navigateToReader,
                onOpenCategories = navController::navigateToCategories,
            )
            categoriesScreen(onBack = navController::popBackStack)
            updatesScreen(onOpenManga = navController::navigateToDetails, onOpenReader = navController::navigateToReader)
            browseScreen(
                onOpenCatalog = navController::navigateToCatalog,
                onOpenGlobalSearch = navController::navigateToGlobalSearch,
                onOpenExtensions = navController::navigateToExtensions,
            )
            extensionsScreen(onBack = navController::popBackStack)
            catalogScreen(
                onBack = navController::popBackStack,
                onOpenManga = navController::navigateToDetails,
                onOpenSourceSettings = { sourceId ->
                    if (sourceId == SourceIds.KOMGA) {
                        navController.navigateToKomgaSettings()
                    } else {
                        navController.navigateToExtensionSourceSettings(sourceId)
                    }
                },
                onOpenWebView = navController::navigateToWebView,
            )
            globalSearchScreen(onBack = navController::popBackStack, onOpenManga = navController::navigateToDetails)
            komgaSettingsScreen(onBack = navController::popBackStack)
            historyScreen(onOpenManga = navController::navigateToDetails, onOpenReader = navController::navigateToReader)
            detailsScreen(
                onBack = navController::popBackStack,
                onRead = navController::navigateToReader,
                onOpenWebView = navController::navigateToWebView,
            )
            webViewScreen(
                onClose = {
                    navController.previousBackStackEntry?.savedStateHandle?.set("reload_requested", true)
                    navController.popBackStack()
                },
            )
            readerScreen(onBack = navController::popBackStack)
            moreGraph(navController)
        }
    }
}

/** The bar stays on tab roots and settings pages; detail screens and the reader get the full screen. */
private fun NavDestination.isTopLevel(): Boolean =
    hasRoute(LibraryRoute::class) || hasRoute(UpdatesRoute::class) || hasRoute(BrowseRoute::class) ||
        hasRoute(HistoryRoute::class) ||
        hierarchy.any { it.hasRoute(TopLevelDestination.More.routeClass) }

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

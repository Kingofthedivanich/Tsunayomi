package app.tsunayomi.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.serialization.Serializable

/** The "More" tab: an entry list plus the settings and about screens behind it. */
@Serializable
data object MoreGraph

@Serializable
internal data object MoreRoute

@Serializable
internal data object SettingsRoute

@Serializable
internal data object AboutRoute

fun NavGraphBuilder.moreGraph(navController: NavController) {
    navigation<MoreGraph>(startDestination = MoreRoute) {
        composable<MoreRoute> {
            MoreScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenAbout = { navController.navigate(AboutRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = navController::popBackStack, onOpenKomga = navController::navigateToKomgaSettings)
        }
        composable<AboutRoute> {
            AboutScreen(onBack = navController::popBackStack)
        }
    }
}

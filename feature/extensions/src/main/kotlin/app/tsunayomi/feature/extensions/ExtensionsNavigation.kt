package app.tsunayomi.feature.extensions

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ExtensionsRoute

@Serializable
data class ExtensionSourceSettingsRoute(val sourceId: Long)

fun NavGraphBuilder.extensionsScreen(onBack: () -> Unit) {
    composable<ExtensionsRoute> { ExtensionsScreen(onBack = onBack) }
    composable<ExtensionSourceSettingsRoute> { SourcePreferencesScreen(onBack = onBack) }
}

fun NavController.navigateToExtensions() = navigate(ExtensionsRoute)

fun NavController.navigateToExtensionSourceSettings(sourceId: Long) = navigate(ExtensionSourceSettingsRoute(sourceId))

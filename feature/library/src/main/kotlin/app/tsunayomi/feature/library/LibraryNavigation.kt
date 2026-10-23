package app.tsunayomi.feature.library

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data object CategoriesRoute

fun NavGraphBuilder.libraryScreen(
    onOpenManga: (mangaId: Long) -> Unit,
    onOpenReader: (mangaId: Long, chapterId: Long) -> Unit,
    onOpenCategories: () -> Unit,
) {
    composable<LibraryRoute> {
        LibraryScreen(onOpenManga = onOpenManga, onOpenReader = onOpenReader, onOpenCategories = onOpenCategories)
    }
}

fun NavGraphBuilder.categoriesScreen(onBack: () -> Unit) {
    composable<CategoriesRoute> { CategoriesScreen(onBack) }
}

fun NavController.navigateToCategories() = navigate(CategoriesRoute)

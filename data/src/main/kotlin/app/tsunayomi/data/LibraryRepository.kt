package app.tsunayomi.data

import app.tsunayomi.core.database.dao.CategoryDao
import app.tsunayomi.core.database.dao.LibraryDao
import app.tsunayomi.core.datastore.UserPreferencesDataSource
import app.tsunayomi.core.model.Category
import app.tsunayomi.core.model.DefaultCategories
import app.tsunayomi.core.model.HistoryItem
import app.tsunayomi.core.model.LibraryManga
import app.tsunayomi.core.model.UpdateItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val libraryDao: LibraryDao,
    private val categoryDao: CategoryDao,
    private val preferences: UserPreferencesDataSource,
) {
    suspend fun ensureDefaultCategories() {
        val initialized = preferences.defaultCategoriesInitialized.first()
        if (initialized) return
        preferences.setDefaultCategoriesInitialized(true)

        val existing = categoryDao.getAll()
        if (existing.isNotEmpty()) return

        val isRu = Locale.getDefault().language == "ru"
        val readingName = if (isRu) DefaultCategories.READING_RU else DefaultCategories.READING_EN
        val completedName = if (isRu) DefaultCategories.COMPLETED_RU else DefaultCategories.COMPLETED_EN
        val planToReadName = if (isRu) DefaultCategories.PLAN_TO_READ_RU else DefaultCategories.PLAN_TO_READ_EN
        val droppedName = if (isRu) DefaultCategories.DROPPED_RU else DefaultCategories.DROPPED_EN

        val readingId = categoryDao.create(readingName)
        categoryDao.create(completedName)
        categoryDao.create(planToReadName)
        categoryDao.create(droppedName)

        val favorites = libraryDao.favoriteIds()
        if (favorites.isNotEmpty()) {
            favorites.forEach { mangaId ->
                if (categoryDao.getCategoryIds(mangaId).isEmpty()) {
                    categoryDao.setMangaCategories(mangaId, listOf(readingId))
                }
            }
        }
    }

    suspend fun getReadingMangaIds(): Set<Long> {
        val readingCategoryIds = categoryDao.getAll()
            .filter { DefaultCategories.isReading(it.name) }
            .map { it.id }
        if (readingCategoryIds.isEmpty()) return emptySet()
        return categoryDao.getMangaIdsForCategories(readingCategoryIds).toSet()
    }
    fun observeLibrary(): Flow<List<LibraryManga>> = combine(
        libraryDao.observeLibrary(),
        categoryDao.observeMangaCategories(),
    ) { rows, links ->
        val categoriesByManga = links.groupBy({ it.mangaId }, { it.categoryId })
        rows.map { row ->
            LibraryManga(
                manga = row.manga.toModel(),
                unreadCount = row.unreadCount,
                totalCount = row.totalCount,
                lastReadAt = row.lastReadAt,
                categoryIds = categoriesByManga[row.manga.id].orEmpty().toSet(),
            )
        }
    }

    fun observeUpdates(): Flow<List<UpdateItem>> = libraryDao.observeUpdates().map { rows ->
        rows.map { UpdateItem(it.chapter.toModel(), it.mangaTitle, it.mangaCoverUrl, it.chapter.fetchedAt) }
    }

    suspend fun favoriteIds(): List<Long> = libraryDao.favoriteIds()

    fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { Category(it.id, it.name, it.order) } }

    fun observeCategoryIds(mangaId: Long): Flow<Set<Long>> = categoryDao.observeCategoryIds(mangaId).map { it.toSet() }

    suspend fun createCategory(name: String): Long = categoryDao.create(name.trim())

    suspend fun renameCategory(id: Long, name: String) = categoryDao.rename(id, name.trim())

    suspend fun deleteCategory(id: Long) = categoryDao.delete(id)

    suspend fun reorderCategories(orderedIds: List<Long>) = categoryDao.reorder(orderedIds)

    suspend fun setMangaCategories(mangaId: Long, categoryIds: Collection<Long>) =
        categoryDao.setMangaCategories(mangaId, categoryIds)

    fun observeHistory(): Flow<List<HistoryItem>> = libraryDao.observeHistory().map { rows ->
        rows.map {
            HistoryItem(
                mangaId = it.mangaId,
                sourceId = it.sourceId,
                title = it.title,
                coverUrl = it.coverUrl,
                chapterId = it.chapterId,
                chapterName = it.chapterName,
                chapterRead = it.chapterRead,
                lastPageRead = it.lastPageRead,
                lastReadAt = it.lastReadAt,
            )
        }
    }

    suspend fun deleteHistory(mangaId: Long) = libraryDao.deleteHistoryForManga(mangaId)

    suspend fun clearHistory() = libraryDao.clearHistory()
}

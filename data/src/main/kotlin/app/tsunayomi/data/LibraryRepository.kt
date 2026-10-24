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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val libraryDao: LibraryDao,
    private val categoryDao: CategoryDao,
    private val preferences: UserPreferencesDataSource,
) {
    // Several screens and the update worker call this at startup; without the lock they would all seed.
    private val seedMutex = Mutex()

    /**
     * Adds whichever status categories are missing, once. Users who already have their own categories still get
     * them: new-chapter notifications only cover "Reading". When "Reading" is created here, library titles that
     * had no category go into it, so they keep getting notifications as before.
     */
    suspend fun ensureDefaultCategories() = seedMutex.withLock {
        val initialized = preferences.defaultCategoriesInitialized.first()
        if (initialized) return@withLock
        preferences.setDefaultCategoriesInitialized(true)

        val existing = categoryDao.getAll().map { it.name }
        val isRu = Locale.getDefault().language == "ru"
        fun missing(matches: (String) -> Boolean) = existing.none(matches)

        val readingId = if (missing(DefaultCategories::isReading)) {
            categoryDao.create(if (isRu) DefaultCategories.READING_RU else DefaultCategories.READING_EN)
        } else {
            null
        }
        if (missing(DefaultCategories::isCompleted)) {
            categoryDao.create(if (isRu) DefaultCategories.COMPLETED_RU else DefaultCategories.COMPLETED_EN)
        }
        if (missing(DefaultCategories::isPlanToRead)) {
            categoryDao.create(if (isRu) DefaultCategories.PLAN_TO_READ_RU else DefaultCategories.PLAN_TO_READ_EN)
        }
        if (missing(DefaultCategories::isDropped)) {
            categoryDao.create(if (isRu) DefaultCategories.DROPPED_RU else DefaultCategories.DROPPED_EN)
        }

        if (readingId != null) {
            libraryDao.favoriteIds().forEach { mangaId ->
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

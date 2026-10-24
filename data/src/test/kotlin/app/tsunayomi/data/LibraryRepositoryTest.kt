package app.tsunayomi.data

import app.tsunayomi.core.database.dao.CategoryDao
import app.tsunayomi.core.database.dao.LibraryDao
import app.tsunayomi.core.database.entity.CategoryEntity
import app.tsunayomi.core.datastore.UserPreferencesDataSource
import app.tsunayomi.core.model.DefaultCategories
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LibraryRepositoryTest {
    private val libraryDao: LibraryDao = mockk(relaxed = true)
    private val categoryDao: CategoryDao = mockk(relaxed = true)
    private val preferences: UserPreferencesDataSource = mockk(relaxed = true)

    private lateinit var repository: LibraryRepository

    @BeforeEach
    fun setup() {
        repository = LibraryRepository(
            libraryDao = libraryDao,
            categoryDao = categoryDao,
            preferences = preferences,
        )
    }

    @Test
    fun `DefaultCategories correctly classifies categories`() {
        assertTrue(DefaultCategories.isReading("Читаю"))
        assertTrue(DefaultCategories.isReading("reading"))
        assertTrue(DefaultCategories.isCompleted("Прочитано"))
        assertTrue(DefaultCategories.isCompleted("Completed"))
        assertTrue(DefaultCategories.isPlanToRead("Буду читать"))
        assertTrue(DefaultCategories.isPlanToRead("plan to read"))
        assertTrue(DefaultCategories.isDropped("Брошено"))
        assertTrue(DefaultCategories.isDropped("Dropped"))

        assertTrue(DefaultCategories.isStatusCategory("Читаю"))
        assertTrue(DefaultCategories.isStatusCategory("Прочитано"))
        assertTrue(DefaultCategories.isStatusCategory("Буду читать"))
        assertTrue(DefaultCategories.isStatusCategory("Брошено"))
        assertFalse(DefaultCategories.isStatusCategory("Custom Category"))
    }

    @Test
    fun `ensureDefaultCategories creates categories when uninitialized and empty`() = runTest {
        every { preferences.defaultCategoriesInitialized } returns flowOf(false)
        coEvery { categoryDao.getAll() } returns emptyList()
        coEvery { categoryDao.create(any()) } returnsMany listOf(1L, 2L, 3L, 4L)
        coEvery { libraryDao.favoriteIds() } returns listOf(100L, 200L)
        coEvery { categoryDao.getCategoryIds(100L) } returns emptyList()
        coEvery { categoryDao.getCategoryIds(200L) } returns listOf(999L)

        repository.ensureDefaultCategories()

        coVerify { preferences.setDefaultCategoriesInitialized(true) }
        coVerify(exactly = 4) { categoryDao.create(any()) }
        // 100L had no categories, should be assigned to Reading (1L)
        coVerify { categoryDao.setMangaCategories(100L, listOf(1L)) }
        // 200L already had category 999L, should not be overwritten
        coVerify(exactly = 0) { categoryDao.setMangaCategories(200L, any()) }
    }

    @Test
    fun `ensureDefaultCategories skips when already initialized`() = runTest {
        every { preferences.defaultCategoriesInitialized } returns flowOf(true)

        repository.ensureDefaultCategories()

        coVerify(exactly = 0) { categoryDao.create(any()) }
    }

    @Test
    fun `getReadingMangaIds returns only manga from reading categories`() = runTest {
        val categories = listOf(
            CategoryEntity(id = 1L, name = "Читаю", order = 0),
            CategoryEntity(id = 2L, name = "Прочитано", order = 1),
            CategoryEntity(id = 3L, name = "Буду читать", order = 2),
            CategoryEntity(id = 4L, name = "Брошено", order = 3),
        )
        coEvery { categoryDao.getAll() } returns categories
        coEvery { categoryDao.getMangaIdsForCategories(listOf(1L)) } returns listOf(10L, 20L)

        val result = repository.getReadingMangaIds()

        assertEquals(setOf(10L, 20L), result)
    }

    @Test
    fun `LibraryManga calculates readCount correctly`() {
        val manga = app.tsunayomi.core.model.Manga(
            id = 1L, sourceId = 0, url = "url", title = "Title", coverUrl = null,
        )
        val item1 = app.tsunayomi.core.model.LibraryManga(
            manga = manga,
            unreadCount = 10,
            totalCount = 50,
            lastReadAt = null,
            categoryIds = emptySet(),
        )
        assertEquals(40, item1.readCount)

        val itemCompleted = app.tsunayomi.core.model.LibraryManga(
            manga = manga,
            unreadCount = 0,
            totalCount = 50,
            lastReadAt = null,
            categoryIds = emptySet(),
        )
        assertEquals(50, itemCompleted.readCount)

        val itemUnreadAll = app.tsunayomi.core.model.LibraryManga(
            manga = manga,
            unreadCount = 50,
            totalCount = 50,
            lastReadAt = null,
            categoryIds = emptySet(),
        )
        assertEquals(0, itemUnreadAll.readCount)
    }
}

package app.tsunayomi.data

import app.tsunayomi.core.database.DatabaseTransactions
import app.tsunayomi.core.database.dao.CategoryDao
import app.tsunayomi.core.database.dao.ChapterDao
import app.tsunayomi.core.database.dao.GlossaryDao
import app.tsunayomi.core.database.dao.HistoryDao
import app.tsunayomi.core.database.dao.MangaDao
import app.tsunayomi.core.database.entity.CategoryEntity
import app.tsunayomi.core.database.entity.ChapterEntity
import app.tsunayomi.core.database.entity.GlossaryTermEntity
import app.tsunayomi.core.database.entity.HistoryEntity
import app.tsunayomi.core.database.entity.MangaEntity
import app.tsunayomi.core.model.BackupResult
import app.tsunayomi.core.model.MangaStatus
import app.tsunayomi.core.model.ReaderMode
import app.tsunayomi.core.model.RestoreResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupRepositoryTest {

    private val mangaDao: MangaDao = mockk()
    private val categoryDao: CategoryDao = mockk()
    private val chapterDao: ChapterDao = mockk()
    private val historyDao: HistoryDao = mockk()
    private val glossaryDao: GlossaryDao = mockk()

    private lateinit var repository: BackupRepository

    @BeforeEach
    fun setup() {
        repository = BackupRepository(
            mangaDao = mangaDao,
            categoryDao = categoryDao,
            chapterDao = chapterDao,
            historyDao = historyDao,
            glossaryDao = glossaryDao,
            transactions = object : DatabaseTransactions {
                override suspend fun <T> run(block: suspend () -> T): T = block()
            },
        )
    }

    @Test
    fun `createBackup writes gzipped json and returns success`() = runTest {
        val cat = CategoryEntity(id = 1L, name = "Reading", order = 0)
        coEvery { categoryDao.getAll() } returns listOf(cat)

        val manga = MangaEntity(
            id = 42L,
            sourceId = 100L,
            url = "/manga/sololeveling",
            title = "Solo Leveling",
            coverUrl = "https://example.com/cover.jpg",
            author = "Chugong",
            artist = "DUBU",
            description = "Hunter Sung Jinwoo",
            genres = listOf("Action", "Fantasy"),
            status = MangaStatus.Completed,
            favorite = true,
            dateAdded = 1000L,
            readerMode = ReaderMode.Webtoon,
            originalLang = "ko",
            detailsUpdatedAt = 2000L,
        )
        coEvery { mangaDao.getFavorites() } returns listOf(manga)
        coEvery { categoryDao.getCategoryIds(42L) } returns listOf(1L)

        val chapter = ChapterEntity(
            id = 101L,
            mangaId = 42L,
            url = "/chapter/1",
            name = "Chapter 1",
            number = 1f,
            scanlator = "Asura",
            uploadedAt = 5000L,
            read = true,
            lastPageRead = 12,
            bookmark = false,
            sourceOrder = 0,
            language = "en",
        )
        coEvery { chapterDao.getByManga(42L) } returns listOf(chapter)

        val history = HistoryEntity(chapterId = 101L, lastReadAt = 6000L, timeRead = 120L)
        coEvery { historyDao.getByManga(42L) } returns listOf(history)

        val term = GlossaryTermEntity(mangaId = 42L, targetLang = "ru", source = "Jinwoo", translation = "Джинву", createdAt = 7000L)
        coEvery { glossaryDao.getAllForManga(42L) } returns listOf(term)

        val output = ByteArrayOutputStream()
        val result = repository.createBackup(output, appVersion = "1.0.0")

        assertTrue(result is BackupResult.Success)
        val success = result as BackupResult.Success
        assertEquals(1, success.mangaCount)
        assertEquals(1, success.categoryCount)
        assertEquals(1, success.chapterCount)

        assertTrue(output.size() > 0)
    }

    @Test
    fun `roundtrip backup and restore restores all data into clean database`() = runTest {
        // 1. Create backup output
        val cat = CategoryEntity(id = 1L, name = "Shonen", order = 0)
        coEvery { categoryDao.getAll() } returns listOf(cat)

        val manga = MangaEntity(
            id = 10L,
            sourceId = 200L,
            url = "/manga/onepiece",
            title = "One Piece",
            coverUrl = "https://example.com/op.jpg",
            author = "Oda",
            artist = null,
            description = "Pirates",
            genres = listOf("Adventure"),
            status = MangaStatus.Ongoing,
            favorite = true,
            dateAdded = 1111L,
            readerMode = ReaderMode.PagedRightToLeft,
            originalLang = "ja",
            detailsUpdatedAt = 2222L,
        )
        coEvery { mangaDao.getFavorites() } returns listOf(manga)
        coEvery { categoryDao.getCategoryIds(10L) } returns listOf(1L)

        val chapter = ChapterEntity(
            id = 500L,
            mangaId = 10L,
            url = "/chapter/1000",
            name = "Chapter 1000",
            number = 1000f,
            scanlator = null,
            uploadedAt = 3333L,
            read = true,
            lastPageRead = 17,
            bookmark = true,
            sourceOrder = 0,
            language = "ja",
        )
        coEvery { chapterDao.getByManga(10L) } returns listOf(chapter)
        coEvery { historyDao.getByManga(10L) } returns listOf(
            HistoryEntity(chapterId = 500L, lastReadAt = 4444L, timeRead = 300L),
        )
        coEvery { glossaryDao.getAllForManga(10L) } returns listOf(
            GlossaryTermEntity(mangaId = 10L, targetLang = "ru", source = "Luffy", translation = "Луффи", createdAt = 5555L),
        )

        val backupBytes = ByteArrayOutputStream().also { repository.createBackup(it) }.toByteArray()

        // 2. Prepare mock responses for restore into a target database
        coEvery { categoryDao.getAll() } returns emptyList() // no categories yet
        coEvery { categoryDao.create("Shonen") } returns 99L // created category with id 99
        coEvery { mangaDao.getBySourceAndUrl(200L, "/manga/onepiece") } returns null // new manga
        coEvery { mangaDao.insert(any()) } returns 77L // inserted with new id 77
        coEvery { categoryDao.getCategoryIds(77L) } returns emptyList()
        coEvery { categoryDao.setMangaCategories(77L, setOf(99L)) } returns Unit
        coEvery { chapterDao.restoreChapters(77L, any()) } returns Unit

        // Chapters after restore
        val restoredChapter = chapter.copy(id = 888L, mangaId = 77L)
        coEvery { chapterDao.getByManga(77L) } returns listOf(restoredChapter)
        coEvery { historyDao.getByChapterId(888L) } returns null
        coEvery { historyDao.upsert(any()) } returns Unit
        coEvery { glossaryDao.insertIfAbsent(any()) } returns Unit

        // 3. Perform restore
        val steps = repository.restoreBackup(ByteArrayInputStream(backupBytes)).toList()
        assertTrue(steps.isNotEmpty())

        val last = steps.last() as RestoreStep.Finished
        assertTrue(last.result is RestoreResult.Success)
        val success = last.result as RestoreResult.Success
        assertEquals(1, success.mangaCount)
        assertEquals(1, success.categoryCount)
        assertEquals(1, success.chapterCount)

        coVerify { categoryDao.create("Shonen") }
        coVerify { mangaDao.insert(match { it.title == "One Piece" && it.sourceId == 200L }) }
        coVerify { categoryDao.setMangaCategories(77L, setOf(99L)) }
        coVerify { chapterDao.restoreChapters(77L, match { it.size == 1 && it[0].name == "Chapter 1000" }) }
        coVerify { historyDao.upsert(match { it.chapterId == 888L && it.lastReadAt == 4444L }) }
        coVerify { glossaryDao.insertIfAbsent(match { it.size == 1 && it[0].source == "Luffy" }) }
    }

    @Test
    fun `restore handles uncompressed plain json`() = runTest {
        val plainJson = """
            {
              "version": 1,
              "createdAt": 1000,
              "categories": [{"name": "Favorites", "order": 0}],
              "manga": []
            }
        """.trimIndent()

        coEvery { categoryDao.getAll() } returns emptyList()
        coEvery { categoryDao.create("Favorites") } returns 1L

        val steps = repository.restoreBackup(ByteArrayInputStream(plainJson.toByteArray())).toList()
        val last = steps.last() as RestoreStep.Finished
        assertTrue(last.result is RestoreResult.Success)
        val success = last.result as RestoreResult.Success
        assertEquals(1, success.categoryCount)
        assertEquals(0, success.mangaCount)
    }

    @Test
    fun `restore reports error on malformed input`() = runTest {
        val invalidBytes = "not a valid json or gzip".toByteArray()
        val steps = repository.restoreBackup(ByteArrayInputStream(invalidBytes)).toList()
        val last = steps.last() as RestoreStep.Finished
        assertTrue(last.result is RestoreResult.Error)
    }
}

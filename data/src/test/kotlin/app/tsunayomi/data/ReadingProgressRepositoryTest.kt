package app.tsunayomi.data

import app.tsunayomi.core.database.dao.ChapterDao
import app.tsunayomi.core.database.dao.HistoryDao
import app.tsunayomi.core.database.entity.ChapterEntity
import app.tsunayomi.core.database.entity.HistoryEntity
import app.tsunayomi.core.datastore.UserPreferencesDataSource
import app.tsunayomi.core.model.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReadingProgressRepositoryTest {

    private val chapterDao: ChapterDao = mockk(relaxed = true)
    private val historyDao: HistoryDao = mockk(relaxed = true)
    private val preferences: UserPreferencesDataSource = mockk()

    private lateinit var repository: ReadingProgressRepository

    @BeforeEach
    fun setup() {
        repository = ReadingProgressRepository(
            chapterDao = chapterDao,
            historyDao = historyDao,
            preferences = preferences,
        )
    }

    @Test
    fun `saveProgress records progress and history when incognito mode is disabled`() = runTest {
        coEvery { preferences.settings } returns flowOf(UserSettings(incognitoMode = false))
        val chapter = ChapterEntity(
            id = 42L,
            mangaId = 1L,
            url = "/ch/1",
            name = "Chapter 1",
            number = 1f,
            scanlator = null,
            uploadedAt = null,
            read = false,
            lastPageRead = 0,
            bookmark = false,
            sourceOrder = 1,
        )
        coEvery { chapterDao.getById(42L) } returns chapter

        repository.saveProgress(chapterId = 42L, page = 5, finished = true)

        coVerify(exactly = 1) { chapterDao.updateProgress(42L, read = true, lastPageRead = 5) }
        coVerify(exactly = 1) { historyDao.upsert(match { it.chapterId == 42L }) }
    }

    @Test
    fun `saveProgress does not record progress or history when incognito mode is active`() = runTest {
        coEvery { preferences.settings } returns flowOf(UserSettings(incognitoMode = true))

        repository.saveProgress(chapterId = 42L, page = 5, finished = true)

        coVerify(exactly = 0) { chapterDao.getById(any()) }
        coVerify(exactly = 0) { chapterDao.updateProgress(any(), any(), any()) }
        coVerify(exactly = 0) { historyDao.upsert(any()) }
    }

    @Test
    fun `setRead forwards call to chapterDao regardless of incognito`() = runTest {
        repository.setRead(listOf(1L, 2L), read = true)

        coVerify(exactly = 1) { chapterDao.setRead(listOf(1L, 2L), true) }
    }
}

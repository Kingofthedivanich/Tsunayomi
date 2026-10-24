package app.tsunayomi.feature.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import app.tsunayomi.core.model.Chapter
import app.tsunayomi.core.model.Manga
import app.tsunayomi.core.model.UserSettings
import app.tsunayomi.data.MangaRepository
import app.tsunayomi.data.ReadingProgressRepository
import app.tsunayomi.data.UserSettingsRepository
import app.tsunayomi.downloads.DownloadManager
import app.tsunayomi.translation.api.PageTranslator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val mangaRepository: MangaRepository = mockk(relaxed = true)
    private val progressRepository: ReadingProgressRepository = mockk(relaxed = true)
    private val settingsRepository: UserSettingsRepository = mockk(relaxed = true)
    private val pageTranslator: PageTranslator = mockk(relaxed = true)
    private val downloadManager: DownloadManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private val sampleManga = Manga(
        id = 1L,
        sourceId = 100L,
        url = "/manga/1",
        title = "Test Manga",
        coverUrl = null,
        translatePages = null,
    )
    private val sampleChapter = Chapter(
        id = 10L,
        mangaId = 1L,
        url = "/chapter/10",
        name = "Chapter 10",
        number = 1f,
        sourceOrder = 0,
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        coEvery { mangaRepository.getChapters(1L) } returns listOf(sampleChapter)
        coEvery { mangaRepository.getPages(any(), any()) } returns emptyList()
        coEvery { downloadManager.downloadedPages(any(), any()) } returns null
        every { settingsRepository.settings } returns flowOf(UserSettings(translateByDefault = false))
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel(manga: Manga = sampleManga): ReaderViewModel {
        coEvery { mangaRepository.getManga(1L) } returns manga
        val handle: SavedStateHandle = mockk(relaxed = true)
        every { handle.toRoute<ReaderRoute>() } returns ReaderRoute(1L, 10L)
        return ReaderViewModel(
            savedStateHandle = handle,
            mangaRepository = mangaRepository,
            progressRepository = progressRepository,
            settingsRepository = settingsRepository,
            pageTranslator = pageTranslator,
            downloadManager = downloadManager,
            context = context,
        )
    }

    @Test
    fun `translation is initialized from manga translatePages when set to true`() = runTest(testDispatcher) {
        val vm = createViewModel(sampleManga.copy(translatePages = true))
        advanceUntilIdle()

        assertTrue(vm.translationOn.value)
    }

    @Test
    fun `translation is initialized from manga translatePages when set to false`() = runTest(testDispatcher) {
        every { settingsRepository.settings } returns flowOf(UserSettings(translateByDefault = true))
        val vm = createViewModel(sampleManga.copy(translatePages = false))
        advanceUntilIdle()

        assertFalse(vm.translationOn.value)
    }

    @Test
    fun `translation falls back to settings translateByDefault when manga translatePages is null`() = runTest(testDispatcher) {
        every { settingsRepository.settings } returns flowOf(UserSettings(translateByDefault = true))
        val vm = createViewModel(sampleManga.copy(translatePages = null))
        advanceUntilIdle()

        assertTrue(vm.translationOn.value)
    }

    @Test
    fun `toggleTranslation toggles state and persists choice in manga`() = runTest(testDispatcher) {
        val vm = createViewModel(sampleManga.copy(translatePages = false))
        advanceUntilIdle()
        assertFalse(vm.translationOn.value)

        vm.toggleTranslation()
        advanceUntilIdle()

        assertTrue(vm.translationOn.value)
        assertEquals(true, vm.state.value.manga?.translatePages)
        coVerify { mangaRepository.setTranslatePages(1L, true) }

        vm.toggleTranslation()
        advanceUntilIdle()

        assertFalse(vm.translationOn.value)
        assertEquals(false, vm.state.value.manga?.translatePages)
        coVerify { mangaRepository.setTranslatePages(1L, false) }
    }
}

package app.tsunayomi.feature.browse

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import app.tsunayomi.data.MangaRepository
import app.tsunayomi.data.SourceManager
import app.tsunayomi.data.UserSettingsRepository
import app.tsunayomi.source.api.CatalogSource
import app.tsunayomi.source.api.FilterList
import app.tsunayomi.source.api.MangaPage
import app.tsunayomi.source.api.SChapter
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SPage
import eu.kanade.tachiyomi.network.CloudflareChallengeException
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class CatalogViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mangaRepository: MangaRepository = mockk(relaxed = true)
    private val settingsRepository: UserSettingsRepository = mockk(relaxed = true)
    private val sourceManager: SourceManager = mockk()

    private val mockSource = object : CatalogSource {
        override val supportsLatest: Boolean = true
        override val id: Long = 100L
        override val name: String = "Test Source"
        override val lang: String = "en"
        override fun getFilters(): FilterList = FilterList()
        override suspend fun getPopular(page: Int): MangaPage = MangaPage(emptyList(), false)
        override suspend fun getLatest(page: Int): MangaPage = MangaPage(emptyList(), false)
        override suspend fun search(query: String, filters: FilterList, page: Int): MangaPage = MangaPage(emptyList(), false)
        override suspend fun getDetails(manga: SManga): SManga = manga
        override suspend fun getChapters(manga: SManga): List<SChapter> = emptyList()
        override suspend fun getPages(chapter: SChapter): List<SPage> = emptyList()
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        coEvery { sourceManager.getCatalog(100L) } returns mockSource
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `loadMore marks isCloudflareError when CloudflareChallengeException is thrown`() = runTest(testDispatcher) {
        val failingSource = object : CatalogSource by mockSource {
            override suspend fun getPopular(page: Int): MangaPage {
                throw CloudflareChallengeException("Cloudflare challenge required")
            }
        }
        coEvery { sourceManager.getCatalog(100L) } returns failingSource

        val savedStateHandle = SavedStateHandle()
        coEvery { savedStateHandle.toRoute<CatalogRoute>() } returns CatalogRoute(100L)

        val vm = CatalogViewModel(
            savedStateHandle = savedStateHandle,
            sourceManager = sourceManager,
            mangaRepository = mangaRepository,
            settingsRepository = settingsRepository,
        )

        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Cloudflare challenge required", state.error)
        assertTrue(state.isCloudflareError)
        assertFalse(state.loading)
    }

    @Test
    fun `reload_requested in savedStateHandle triggers reload and clears flag`() = runTest(testDispatcher) {
        var callCount = 0
        val countingSource = object : CatalogSource by mockSource {
            override suspend fun getPopular(page: Int): MangaPage {
                callCount++
                return MangaPage(emptyList(), false)
            }
        }
        coEvery { sourceManager.getCatalog(100L) } returns countingSource

        val savedStateHandle = SavedStateHandle()
        coEvery { savedStateHandle.toRoute<CatalogRoute>() } returns CatalogRoute(100L)

        val vm = CatalogViewModel(
            savedStateHandle = savedStateHandle,
            sourceManager = sourceManager,
            mangaRepository = mangaRepository,
            settingsRepository = settingsRepository,
        )

        advanceUntilIdle()
        assertEquals(1, callCount)

        savedStateHandle["reload_requested"] = true
        advanceUntilIdle()

        assertEquals(2, callCount)
        assertEquals(false, savedStateHandle["reload_requested"])
    }
}

package app.tsunayomi.feature.browse

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import app.tsunayomi.data.SourceManager
import app.tsunayomi.source.api.CatalogSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WebViewViewModelTest {

    private val sourceManager: SourceManager = mockk()
    private val savedStateHandle: SavedStateHandle = mockk(relaxed = true)
    private val mockSource: CatalogSource = mockk()

    @BeforeEach
    fun setUp() {
        mockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    @Test
    fun `getHeaders returns source webHeaders when source is present`() {
        every { savedStateHandle.toRoute<WebViewRoute>() } returns WebViewRoute("https://example.com", 123L)
        every { mockSource.webHeaders() } returns mapOf("User-Agent" to "CustomUA", "Referer" to "https://example.com/")
        every { sourceManager.get(123L) } returns mockSource

        val viewModel = WebViewViewModel(savedStateHandle, sourceManager)
        val headers = viewModel.getHeaders(123L)

        assertEquals("CustomUA", headers["User-Agent"])
        assertEquals("https://example.com/", headers["Referer"])
    }

    @Test
    fun `getHeaders returns empty map when source not found`() {
        every { savedStateHandle.toRoute<WebViewRoute>() } returns WebViewRoute("https://example.com", 999L)
        every { sourceManager.get(999L) } returns null

        val viewModel = WebViewViewModel(savedStateHandle, sourceManager)
        val headers = viewModel.getHeaders(999L)

        assertEquals(emptyMap<String, String>(), headers)
    }
}

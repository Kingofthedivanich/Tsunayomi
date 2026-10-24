package app.tsunayomi.source.kotatsu

import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SPage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder

class KotatsuCatalogSourceTest {

    private val mockParser = mockk<MangaParser>(relaxed = true)

    @Test
    fun `metadata matches parser source`() {
        val source = KotatsuCatalogSource(MangaParserSource.MANGACHAN, mockParser)
        assertEquals("Манга-тян", source.name)
        assertEquals("ru", source.lang)
    }

    @Test
    fun `chapters are returned in ascending reading order`() = runTest {
        val source = KotatsuCatalogSource(MangaParserSource.LIKEMANGA, mockParser)

        val ch1 = MangaChapter(id = 1L, title = "Ch 1", number = 1.0f, volume = 0, url = "/ch1", scanlator = "", uploadDate = 0L, branch = "", source = MangaParserSource.LIKEMANGA)
        val ch2 = MangaChapter(id = 2L, title = "Ch 2", number = 2.0f, volume = 0, url = "/ch2", scanlator = "", uploadDate = 0L, branch = "", source = MangaParserSource.LIKEMANGA)
        val ch3 = MangaChapter(id = 3L, title = "Ch 3", number = 3.0f, volume = 0, url = "/ch3", scanlator = "", uploadDate = 0L, branch = "", source = MangaParserSource.LIKEMANGA)

        // Simulating parser returning chapters in descending order (newest first)
        val mockDetails = Manga(
            id = 1L,
            title = "Test",
            altTitles = emptySet(),
            url = "/manga/1",
            publicUrl = "https://likemanga.ink/manga/1",
            rating = 0f,
            contentRating = org.koitharu.kotatsu.parsers.model.ContentRating.SAFE,
            coverUrl = "",
            tags = emptySet(),
            state = org.koitharu.kotatsu.parsers.model.MangaState.ONGOING,
            authors = emptySet(),
            largeCoverUrl = null,
            description = "",
            chapters = listOf(ch3, ch2, ch1),
            source = MangaParserSource.LIKEMANGA,
        )
        coEvery { mockParser.getDetails(any()) } returns mockDetails

        val manga = SManga(url = "/manga/1", title = "Test Manga", coverUrl = null)
        val result = source.getChapters(manga)

        assertEquals(3, result.size)
        assertEquals(1.0f, result[0].number)
        assertEquals("Ch 1", result[0].name)
        assertEquals(2.0f, result[1].number)
        assertEquals(3.0f, result[2].number)
    }

    @Test
    fun `getPages injects Referer header`() = runTest {
        every { mockParser.domain } returns "likemanga.ink"
        every { mockParser.getRequestHeaders() } returns Headers.headersOf("User-Agent", "TestAgent")

        val source = KotatsuCatalogSource(MangaParserSource.LIKEMANGA, mockParser)

        val mockKotatsuPage = MangaPage(id = 1L, url = "https://cdn.example.com/p1.jpg", preview = null, source = MangaParserSource.LIKEMANGA)
        coEvery { mockParser.getPages(any()) } returns listOf(mockKotatsuPage)
        coEvery { mockParser.getPageUrl(any()) } returns "https://cdn.example.com/p1.jpg"

        val chapter = app.tsunayomi.source.api.SChapter(url = "/ch1", name = "Chapter 1", number = 1f)
        val pages = source.getPages(chapter)

        assertEquals(1, pages.size)
        val remotePage = pages[0] as SPage.Remote
        assertEquals("https://cdn.example.com/p1.jpg", remotePage.imageUrl)
        assertEquals("https://likemanga.ink/", remotePage.headers["Referer"])
        assertEquals("TestAgent", remotePage.headers["User-Agent"])
    }

    @Test
    fun `webUrl resolves full and relative urls`() {
        every { mockParser.domain } returns "manga-chan.me"
        val source = KotatsuCatalogSource(MangaParserSource.MANGACHAN, mockParser)

        assertEquals("https://manga-chan.me", source.webUrl())
        assertEquals("https://manga-chan.me/manga/1", source.webUrl(SManga(url = "/manga/1", title = "Test", coverUrl = null)))
        assertEquals("https://other.com/manga/2", source.webUrl(SManga(url = "https://other.com/manga/2", title = "Test", coverUrl = null)))
    }
}

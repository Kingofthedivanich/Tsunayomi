package app.tsunayomi.source.mangadex

import app.tsunayomi.source.api.ChaptersOnlyExternalException
import app.tsunayomi.source.api.Filter
import app.tsunayomi.source.api.FilterList
import app.tsunayomi.source.api.ReadingDirection
import app.tsunayomi.source.api.SChapter
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SPage
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale
import org.junit.jupiter.api.assertThrows

class MangaDexSourceTest {
    private val server = MockWebServer()
    private var languages: Set<String> = setOf("ru")
    private val saved = mutableListOf<Set<String>>()
    private lateinit var source: MangaDexSource
    private lateinit var defaultLocale: Locale

    @BeforeEach
    fun setUp() {
        server.start()
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ru"))
        source = MangaDexSource(
            client = OkHttpClient(),
            languages = { languages },
            saveLanguages = { saved += it; languages = it },
            apiUrl = server.url("/"),
            coversUrl = "https://covers.test",
        )
    }

    @AfterEach
    fun tearDown() {
        server.close()
        Locale.setDefault(defaultLocale)
    }

    private fun enqueue(resource: String) {
        val body = javaClass.getResource("/mangadex/$resource")!!.readText()
        server.enqueue(MockResponse.Builder().body(body).build())
    }

    @Test
    fun `popular parses list and sends language and rating filters`() = runTest {
        enqueue("manga_list.json")

        val page = source.getPopular(1)

        val request = server.takeRequest().url
        assertEquals("/manga", request.encodedPath)
        assertEquals("ru", request.queryParameter("availableTranslatedLanguage[]"))
        assertEquals("desc", request.queryParameter("order[followedCount]"))
        assertEquals(listOf("safe", "suggestive"), request.queryParameterValues("contentRating[]"))
        assertEquals("0", request.queryParameter("offset"))

        assertTrue(page.hasNext)
        val first = page.items[0]
        assertEquals("Тестовая манга", first.title)
        assertEquals("https://covers.test/11111111-1111-1111-1111-111111111111/cover.png.256.jpg", first.coverUrl)
        assertNull(first.description) // "description": [] must not break parsing
        assertEquals(ReadingDirection.RightToLeft, first.readingDirection)
        assertEquals("ja", first.originalLang)

        val second = page.items[1]
        assertEquals("Strip Story", second.title)
        assertEquals("A webtoon.", second.description)
        assertEquals(ReadingDirection.Webtoon, second.readingDirection)
        assertNull(second.coverUrl)
    }

    @Test
    fun `search without explicit filters uses relevance only with a query`() = runTest {
        enqueue("manga_list.json")
        source.search("abc", FilterList(), page = 2)
        val url = server.takeRequest().url
        assertEquals("abc", url.queryParameter("title"))
        assertEquals("30", url.queryParameter("offset"))
    }

    @Test
    fun `details prefer the source language and merge authors`() = runTest {
        enqueue("manga_details.json")

        val manga = source.getDetails(SManga(url = "11111111-1111-1111-1111-111111111111", title = "x", coverUrl = null))

        assertEquals("Русское описание", manga.description)
        assertEquals("Writer One", manga.author)
        assertEquals("Writer One, Artist Two", manga.artist)
        assertEquals(SManga.Status.Hiatus, manga.status)
        assertTrue(manga.coverUrl!!.endsWith(".512.jpg"))
    }

    @Test
    fun `chapters are paged, skip external ones and are ordered by number`() = runTest {
        enqueue("feed_page1.json")
        enqueue("feed_page2.json")

        val chapters = source.getChapters(SManga(url = "m1", title = "x", coverUrl = null))

        assertEquals("0", server.takeRequest().url.queryParameter("offset"))
        assertEquals("2", server.takeRequest().url.queryParameter("offset"))
        assertEquals(listOf("ch-1", "ch-2", "ch-os"), chapters.map { it.url })
        assertEquals("Vol. 1 Ch. 1 - Start", chapters[0].name)
        assertEquals("Group A & Group B", chapters[1].scanlator)
        assertEquals("Oneshot", chapters[2].name)
        assertEquals(-1f, chapters[2].number)
        assertEquals(1714557600000L, chapters[1].uploadedAt)
        assertEquals("en", chapters[0].language)
    }

    @Test
    fun `only external chapters are reported as such`() = runTest {
        enqueue("feed_empty.json")
        enqueue("feed_external_total.json")

        val error = assertThrows<ChaptersOnlyExternalException> {
            source.getChapters(SManga(url = "licensed", title = "x", coverUrl = null))
        }

        assertEquals(220, error.externalCount)
        server.takeRequest()
        val probe = server.takeRequest().url
        assertEquals("1", probe.queryParameter("limit"))
        assertNull(probe.queryParameter("includeExternalUrl"))
    }

    @Test
    fun `a series without any chapters is just empty`() = runTest {
        enqueue("feed_empty.json")
        enqueue("feed_empty.json")
        assertTrue(source.getChapters(SManga(url = "new", title = "x", coverUrl = null)).isEmpty())
    }

    @Test
    fun `pages are built from the at-home server`() = runTest {
        enqueue("at_home.json")

        val pages = source.getPages(SChapter(url = "ch-1", name = "x", number = 1f))

        assertEquals("/at-home/server/ch-1", server.takeRequest().url.encodedPath)
        assertEquals(
            listOf(
                "https://node.example.net/token123/data/0123456789abcdef0123456789abcdef/1.png",
                "https://node.example.net/token123/data/0123456789abcdef0123456789abcdef/2.png",
            ),
            pages.map { (it as SPage.Remote).imageUrl },
        )
        assertTrue(pages.all { (it as SPage.Remote).headers.isEmpty() })
    }

    @Test
    fun `old per-language source ids resolve to the single source`() {
        assertTrue(app.tsunayomi.source.api.sourceIdOf("MangaDex", "en") in source.legacyIds)
        assertTrue(app.tsunayomi.source.api.sourceIdOf("MangaDex", "ru") in source.legacyIds)
        assertFalse(source.id in source.legacyIds)
    }

    @Test
    fun `language filter drives the search and is remembered for chapter lists`() = runTest {
        enqueue("manga_list.json")
        val filters = source.getFilters()
        val group = filters.first { it is Filter.Group<*> && it.name == MangaDexFilters.LANGUAGES_GROUP } as Filter.Group<*>
        group.state.forEachIndexed { index, box ->
            (box as Filter.CheckBox).state = MangaDexSource.LANGUAGES[index] in setOf("en", "ja")
        }

        source.search("", filters, page = 1)

        assertEquals(listOf("en", "ja"), server.takeRequest().url.queryParameterValues("availableTranslatedLanguage[]"))
        assertEquals(listOf(setOf("en", "ja")), saved)

        enqueue("feed_page1.json")
        enqueue("feed_page2.json")
        source.getChapters(SManga(url = "m1", title = "x", coverUrl = null))
        assertEquals(listOf("en", "ja"), server.takeRequest().url.queryParameterValues("translatedLanguage[]"))
    }

    @Test
    fun `default languages are the app language plus English`() {
        assertEquals(setOf("ru", "en"), MangaDexSource.defaultLanguages("ru"))
        assertEquals(setOf("en"), MangaDexSource.defaultLanguages("en"))
        assertEquals(setOf("en"), MangaDexSource.defaultLanguages("xx"))
    }
}

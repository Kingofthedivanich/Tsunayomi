package app.tsunayomi.source.asura

import app.tsunayomi.source.api.Filter
import app.tsunayomi.source.api.FilterList
import app.tsunayomi.source.api.ReadingDirection
import app.tsunayomi.source.api.SChapter
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SPage
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AsuraSourceTest {
    private val server = MockWebServer()
    private lateinit var source: AsuraSource
    private val client = OkHttpClient()

    @BeforeEach
    fun setUp() {
        server.start()
        source = AsuraSource(client, apiBaseUrl = server.url("/api/"))
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `getPopular parses series list and pagination`() = runTest {
        val json = """
            {
                "data": [
                    {
                        "id": 2165,
                        "slug": "overgeared",
                        "title": "Overgeared",
                        "description": "Shin Youngwoo in Satisfy VR.",
                        "cover": "https://cdn.asurascans.com/covers/overgeared.webp",
                        "status": "ongoing",
                        "author": "Park Saenal",
                        "artist": "Team ARGO",
                        "rating": 9.1,
                        "genres": [
                            {"id": 1, "name": "Action", "slug": "action"},
                            {"id": 4, "name": "Adventure", "slug": "adventure"}
                        ]
                    }
                ],
                "meta": {
                    "total": 357,
                    "per_page": 20,
                    "has_more": true
                }
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(json).build())

        val result = source.getPopular(1)
        val recorded = server.takeRequest()

        assertEquals("popular", recorded.url.queryParameter("sort"))
        assertEquals("desc", recorded.url.queryParameter("order"))
        assertEquals("1", recorded.url.queryParameter("page"))
        assertTrue(result.hasNext)
        assertEquals(1, result.items.size)

        val item = result.items[0]
        assertEquals("overgeared", item.url)
        assertEquals("Overgeared", item.title)
        assertEquals("https://cdn.asurascans.com/covers/overgeared.webp", item.coverUrl)
        assertEquals("Park Saenal", item.author)
        assertEquals("Team ARGO", item.artist)
        assertEquals(SManga.Status.Ongoing, item.status)
        assertEquals(ReadingDirection.Webtoon, item.readingDirection)
        assertEquals(listOf("Action", "Adventure"), item.genres)
    }

    @Test
    fun `getLatest requests sort=update and order=desc`() = runTest {
        val json = """
            {
                "data": [],
                "meta": {
                    "total": 0,
                    "has_more": false
                }
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(json).build())

        val result = source.getLatest(1)
        val recorded = server.takeRequest()

        assertEquals("update", recorded.url.queryParameter("sort"))
        assertEquals("desc", recorded.url.queryParameter("order"))
        assertFalse(result.hasNext)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `search applies query and filters`() = runTest {
        val json = """
            {
                "data": [
                    {
                        "id": 100,
                        "slug": "swordmaster",
                        "title": "Swordmaster",
                        "cover": "https://cdn.asurascans.com/covers/sword.webp",
                        "status": "completed"
                    }
                ],
                "meta": { "total": 1, "has_more": false }
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(json).build())

        val filters = FilterList(
            Filter.Select("Sort", AsuraFilters.sortOptions.map { it.first }, state = 2), // rating
            Filter.Select("Status", AsuraFilters.statusOptions.map { it.first }, state = 2), // completed
            Filter.Select("Type", AsuraFilters.typeOptions.map { it.first }, state = 0), // comic
        )

        val result = source.search("sword", filters, page = 2)
        val recorded = server.takeRequest()

        assertEquals("sword", recorded.url.queryParameter("search"))
        assertEquals("rating", recorded.url.queryParameter("sort"))
        assertEquals("desc", recorded.url.queryParameter("order"))
        assertEquals("completed", recorded.url.queryParameter("status"))
        assertEquals("comic", recorded.url.queryParameter("type"))
        assertEquals("2", recorded.url.queryParameter("page"))

        assertEquals(1, result.items.size)
        assertEquals("swordmaster", result.items[0].url)
        assertEquals(SManga.Status.Completed, result.items[0].status)
    }

    @Test
    fun `getDetails returns complete SManga details`() = runTest {
        val json = """
            {
                "series": {
                    "id": 2165,
                    "slug": "overgeared",
                    "title": "Overgeared",
                    "description": "Full VR gaming story.",
                    "cover": "https://cdn.asurascans.com/covers/overgeared.webp",
                    "status": "ongoing",
                    "author": "Park Saenal",
                    "genres": [{"id": 1, "name": "Action", "slug": "action"}]
                }
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(json).build())

        val manga = SManga(url = "overgeared", title = "Overgeared", coverUrl = null)
        val details = source.getDetails(manga)
        val recorded = server.takeRequest()

        assertEquals("/api/series/overgeared", recorded.url.encodedPath)
        assertEquals("Overgeared", details.title)
        assertEquals("Full VR gaming story.", details.description)
        assertEquals("Park Saenal", details.author)
        assertEquals(SManga.Status.Ongoing, details.status)
        assertEquals(ReadingDirection.Webtoon, details.readingDirection)
    }

    @Test
    fun `getChapters fetches chapters and filters out premium chapters`() = runTest {
        val json = """
            {
                "data": [
                    {
                        "id": 300,
                        "series_id": 2165,
                        "number": 345,
                        "title": "King of Basic Attacks",
                        "slug": "chapter-345",
                        "is_premium": true,
                        "published_at": "2026-10-23T20:38:08Z"
                    },
                    {
                        "id": 299,
                        "series_id": 2165,
                        "number": 344.5,
                        "title": "Special Interlude",
                        "slug": "chapter-344-5",
                        "is_premium": false,
                        "published_at": "2026-10-20T12:00:00Z"
                    },
                    {
                        "id": 298,
                        "series_id": 2165,
                        "number": 344,
                        "title": "Saharan vs. Valhalla",
                        "slug": "chapter-344",
                        "is_premium": false,
                        "published_at": "2026-10-16T10:00:00Z"
                    }
                ]
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(json).build())

        val manga = SManga(url = "overgeared", title = "Overgeared", coverUrl = null)
        val chapters = source.getChapters(manga)
        val recorded = server.takeRequest()

        assertEquals("/api/series/overgeared/chapters", recorded.url.encodedPath)
        // Chapter 345 is premium, so only 344.5 and 344 should be returned
        assertEquals(2, chapters.size)

        assertEquals("overgeared/chapter-344-5", chapters[1].url)
        assertEquals("Chapter 344.5 - Special Interlude", chapters[1].name)
        assertEquals(344.5f, chapters[1].number)
        assertEquals("Asura Scans", chapters[1].scanlator)
        assertEquals("en", chapters[1].language)
        assertTrue((chapters[1].uploadedAt ?: 0L) > 0L)

        assertEquals("overgeared/chapter-344", chapters[0].url)
        assertEquals("Chapter 344 - Saharan vs. Valhalla", chapters[0].name)
        assertEquals(344.0f, chapters[0].number)
    }

    @Test
    fun `getPages extracts page URLs with index`() = runTest {
        val json = """
            {
                "data": {
                    "chapter": {
                        "id": 298,
                        "slug": "chapter-344",
                        "pages": [
                            {"url": "https://cdn.asurascans.com/images/1.webp", "width": 800, "height": 1200},
                            {"url": "https://cdn.asurascans.com/images/2.webp", "width": 800, "height": 1200}
                        ]
                    }
                }
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(json).build())

        val chapter = SChapter(url = "overgeared/chapter-344", name = "Chapter 344", number = 344f)
        val pages = source.getPages(chapter)
        val recorded = server.takeRequest()

        assertEquals("/api/series/overgeared/chapters/chapter-344", recorded.url.encodedPath)
        assertEquals(2, pages.size)

        val p1 = pages[0] as SPage.Remote
        val p2 = pages[1] as SPage.Remote
        assertEquals(0, p1.index)
        assertEquals("https://cdn.asurascans.com/images/1.webp", p1.imageUrl)
        assertEquals(1, p2.index)
        assertEquals("https://cdn.asurascans.com/images/2.webp", p2.imageUrl)
    }

    @Test
    fun `AsuraInterceptor attaches Referer and User-Agent to asurascans domains`() {
        val interceptor = AsuraInterceptor()
        val request = Request.Builder().url("https://cdn.asurascans.com/covers/image.webp").build()
        val captured = slot<Request>()
        val chain = mockk<okhttp3.Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(capture(captured)) } returns mockk(relaxed = true)

        interceptor.intercept(chain)
        assertEquals("https://asurascans.com/", captured.captured.header("Referer"))
        assertNotNull(captured.captured.header("User-Agent"))
    }

    @Test
    fun `AsuraInterceptor passes through non-asura hosts without changes`() {
        val interceptor = AsuraInterceptor()
        val request = Request.Builder().url("https://example.com/test.jpg").build()
        val chain = mockk<okhttp3.Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns mockk(relaxed = true)

        interceptor.intercept(chain)
    }
}

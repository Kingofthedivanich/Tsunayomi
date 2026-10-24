package app.tsunayomi.source.remanga

import app.tsunayomi.source.api.Filter
import app.tsunayomi.source.api.FilterList
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
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RemangaSourceTest {
    private val server = MockWebServer()
    private lateinit var source: RemangaSource
    private val client = OkHttpClient()

    @BeforeEach
    fun setUp() {
        server.start()
        source = RemangaSource(client, apiBaseUrl = server.url("/api/"))
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `getPopular parses catalog items and pagination`() = runTest {
        val json = """
            {
                "content": [
                    {
                        "id": 2060,
                        "dir": "solo-leveling_",
                        "rus_name": "Поднятие уровня в одиночку",
                        "main_name": "Solo Leveling",
                        "cover": {
                            "high": "/media/titles/solo-leveling_/cover_high.webp",
                            "mid": "/media/titles/solo-leveling_/cover_mid.webp"
                        },
                        "genres": [
                            {"id": 2, "name": "Экшен"},
                            {"id": 38, "name": "Фэнтези"}
                        ]
                    }
                ],
                "props": {
                    "total_items": 100,
                    "total_pages": 4,
                    "page": 1
                }
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(json).build())

        val result = source.getPopular(1)
        val recorded = server.takeRequest()

        assertEquals("-rating", recorded.url.queryParameter("ordering"))
        assertEquals("30", recorded.url.queryParameter("count"))
        assertEquals("1", recorded.url.queryParameter("page"))
        assertTrue(result.hasNext)
        assertEquals(1, result.items.size)

        val item = result.items[0]
        assertEquals("solo-leveling_", item.url)
        assertEquals("Поднятие уровня в одиночку", item.title)
        assertEquals("ru", item.originalLang)
        assertEquals("https://remanga.org/media/titles/solo-leveling_/cover_high.webp", item.coverUrl)
        assertEquals(listOf("Экшен", "Фэнтези"), item.genres)
    }

    @Test
    fun `getLatest requests ordering by chapter_date`() = runTest {
        val json = """
            {
                "content": [],
                "props": { "total_pages": 1, "page": 1 }
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(json).build())

        val result = source.getLatest(1)
        val recorded = server.takeRequest()

        assertEquals("-chapter_date", recorded.url.queryParameter("ordering"))
        assertFalse(result.hasNext)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `search applies query and filters`() = runTest {
        val json = """
            {
                "content": [
                    {
                        "id": 51969,
                        "dir": "estate-designer",
                        "rus_name": "Система всемогущего дизайнера",
                        "main_name": "The Greatest Estate Developer",
                        "cover": { "mid": "/media/estate.webp" }
                    }
                ],
                "props": { "total_pages": 1, "page": 1 }
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(json).build())

        val filters = FilterList(
            Filter.Select("Сортировка", RemangaFilters.sortOptions.map { it.first }, state = 1), // -votes
            Filter.Select("Тип произведения", RemangaFilters.typeOptions.map { it.first }, state = 1), // Манхва
            Filter.Select("Возрастной рейтинг", RemangaFilters.ageRatingOptions.map { it.first }, state = 2), // 16+
        )

        val result = source.search("дизайнер", filters, page = 1)
        val recorded = server.takeRequest()

        assertEquals("дизайнер", recorded.url.queryParameter("query"))
        assertEquals("-votes", recorded.url.queryParameter("ordering"))
        assertEquals("Манхва", recorded.url.queryParameter("type"))
        assertEquals("1", recorded.url.queryParameter("age_limit"))
        assertEquals(1, result.items.size)
        assertEquals("Система всемогущего дизайнера", result.items[0].title)
    }

    @Test
    fun `getDetails parses title details, status, description and genres`() = runTest {
        val json = """
            {
                "content": {
                    "id": 51969,
                    "dir": "estate-designer",
                    "rus_name": "Система всемогущего дизайнера",
                    "main_name": "The Greatest Estate Developer",
                    "description": "<p>Ким Сухо оказывается в теле дворянина-бездельника Ллойда.</p>",
                    "cover": { "high": "/media/estate_high.webp" },
                    "status": { "id": 2, "name": "Продолжается" },
                    "genres": [
                        {"id": 2, "name": "Комедия"},
                        {"id": 38, "name": "Фэнтези"}
                    ],
                    "branches": [
                        {
                            "id": 31134,
                            "count_chapters": 200,
                            "publishers": [{"id": 1, "name": "tfoxs"}]
                        }
                    ]
                }
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(json).build())

        val manga = SManga(url = "estate-designer", title = "Estate", coverUrl = null)
        val details = source.getDetails(manga)
        val recorded = server.takeRequest()

        assertEquals("/api/titles/estate-designer", recorded.url.encodedPath)
        assertEquals("Система всемогущего дизайнера", details.title)
        assertEquals("ru", details.originalLang)
        assertEquals("https://remanga.org/media/estate_high.webp", details.coverUrl)
        assertEquals("Ким Сухо оказывается в теле дворянина-бездельника Ллойда.", details.description)
        assertEquals(SManga.Status.Ongoing, details.status)
        assertEquals(listOf("Комедия", "Фэнтези"), details.genres)
    }

    @Test
    fun `getChapters fetches chapters across branches and skips unpaid chapters`() = runTest {
        val titleJson = """
            {
                "content": {
                    "id": 51969,
                    "dir": "estate-designer",
                    "branches": [
                        { "id": 31134, "count_chapters": 2 }
                    ]
                }
            }
        """.trimIndent()

        val chaptersJson = """
            {
                "content": [
                    {
                        "id": 101,
                        "tome": 1,
                        "chapter": "1",
                        "name": "Пробуждение",
                        "upload_date": "2026-01-01T12:00:00",
                        "is_paid": false,
                        "is_bought": false,
                        "publishers": [{"id": 1, "name": "tfoxs"}]
                    },
                    {
                        "id": 102,
                        "tome": 1,
                        "chapter": "2",
                        "name": "План",
                        "upload_date": "2026-01-02T12:00:00",
                        "is_paid": true,
                        "is_bought": false,
                        "publishers": [{"id": 1, "name": "tfoxs"}]
                    },
                    {
                        "id": 103,
                        "tome": 1,
                        "chapter": "3",
                        "name": "Строительство",
                        "upload_date": "2026-01-03T12:00:00",
                        "is_paid": true,
                        "is_bought": true,
                        "publishers": [{"id": 1, "name": "tfoxs"}]
                    }
                ]
            }
        """.trimIndent()

        server.enqueue(MockResponse.Builder().body(titleJson).build())
        server.enqueue(MockResponse.Builder().body(chaptersJson).build())

        val manga = SManga(url = "estate-designer", title = "Estate", coverUrl = null)
        val chapters = source.getChapters(manga)

        // Chapter 102 should be filtered out because it is paid and not bought.
        // Remaining: 101 and 103. In reading order: 101, then 103.
        assertEquals(2, chapters.size)

        assertEquals("103", chapters[1].url)
        assertEquals("Том 1 Гл. 3 - Строительство", chapters[1].name)
        assertEquals(3.0f, chapters[1].number)
        assertEquals("tfoxs", chapters[1].scanlator)
        assertTrue((chapters[1].uploadedAt ?: 0L) > 0L)

        assertEquals("101", chapters[0].url)
        assertEquals("Том 1 Гл. 1 - Пробуждение", chapters[0].name)
        assertEquals(1.0f, chapters[0].number)
    }

    @Test
    fun `getPages extracts page URLs from nested arrays and converts to absolute URLs`() = runTest {
        val chapterJson = """
            {
                "content": {
                    "id": 101,
                    "pages": [
                        [
                            {"id": 1, "link": "https://img.reimg.org/images/p1.webp"},
                            {"id": 2, "link": "/media/p2.webp"}
                        ],
                        [
                            {"id": 3, "link": "https://img.reimg.org/images/p3.webp"}
                        ]
                    ]
                }
            }
        """.trimIndent()
        server.enqueue(MockResponse.Builder().body(chapterJson).build())

        val chapter = SChapter(url = "101", name = "Гл. 1", number = 1f)
        val pages = source.getPages(chapter)

        assertEquals(3, pages.size)
        val p1 = pages[0] as SPage.Remote
        val p2 = pages[1] as SPage.Remote
        val p3 = pages[2] as SPage.Remote

        assertEquals(0, p1.index)
        assertEquals("https://img.reimg.org/images/p1.webp", p1.imageUrl)
        assertEquals(1, p2.index)
        assertEquals("https://remanga.org/media/p2.webp", p2.imageUrl)
        assertEquals(2, p3.index)
        assertEquals("https://img.reimg.org/images/p3.webp", p3.imageUrl)
    }

    @Test
    fun `RemangaInterceptor attaches Referer and User-Agent headers to remanga and reimg hosts`() {
        val interceptor = RemangaInterceptor()
        val request = Request.Builder().url("https://img-reserve.reimg2.org/images/test.webp").build()
        val captured = slot<Request>()
        val chain = mockk<okhttp3.Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(capture(captured)) } returns mockk(relaxed = true)

        interceptor.intercept(chain)
        assertEquals("https://remanga.org/", captured.captured.header("Referer"))
        assertNotNull(captured.captured.header("User-Agent"))
    }

    @Test
    fun `RemangaInterceptor falls back to img-reserve when primary host returns 403`() {
        val interceptor = RemangaInterceptor()
        val request = Request.Builder().url("https://img.reimg.org/images/chapter1.webp").build()

        val capturedRequests = mutableListOf<Request>()
        val chain = mockk<okhttp3.Interceptor.Chain>()
        every { chain.request() } returns request

        val blockedResponse = mockk<Response>(relaxed = true) {
            every { code } returns 403
        }
        val successResponse = mockk<Response>(relaxed = true) {
            every { code } returns 200
        }

        every { chain.proceed(capture(capturedRequests)) } returns blockedResponse andThen successResponse

        val result = interceptor.intercept(chain)
        assertEquals(200, result.code)
        assertEquals(2, capturedRequests.size)
        assertEquals("img.reimg.org", capturedRequests[0].url.host)
        assertEquals("img-reserve.reimg2.org", capturedRequests[1].url.host)
    }

    @Test
    fun `RemangaInterceptor passes through non-remanga hosts without changes`() {
        val interceptor = RemangaInterceptor()
        val request = Request.Builder().url("https://example.com/image.jpg").build()
        val chain = mockk<okhttp3.Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns mockk(relaxed = true)

        interceptor.intercept(chain)
    }
}

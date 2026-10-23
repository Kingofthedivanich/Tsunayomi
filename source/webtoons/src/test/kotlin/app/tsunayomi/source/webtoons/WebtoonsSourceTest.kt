package app.tsunayomi.source.webtoons

import app.tsunayomi.source.api.Filter
import app.tsunayomi.source.api.FilterList
import app.tsunayomi.source.api.ReadingDirection
import app.tsunayomi.source.api.SChapter
import app.tsunayomi.source.api.SManga
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
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class WebtoonsSourceTest {
    private val server = MockWebServer()
    private lateinit var source: WebtoonsSource
    private val client = OkHttpClient()

    @BeforeEach
    fun setUp() {
        server.start()
        source = WebtoonsSource(client, baseUrl = server.url("").toString().removeSuffix("/"))
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `getPopular parses trending manga items`() = runTest {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <ul class="webtoon_list">
                    <li>
                        <a href="/en/action/tower-of-god/list?title_no=95" class="link _originals_title_a">
                            <div class="image_wrap">
                                <img src="https://webtoon-phinf.pstatic.net/cover95.jpg" />
                            </div>
                            <div class="info_text">
                                <div class="genre">Action</div>
                                <strong class="title">Tower of God</strong>
                                <div class="author">SIU</div>
                            </div>
                        </a>
                    </li>
                    <li>
                        <a href="/en/romance/lore-olympus/list?title_no=1320" class="link _originals_title_a">
                            <div class="image_wrap">
                                <img src="https://webtoon-phinf.pstatic.net/cover1320.jpg" />
                            </div>
                            <div class="info_text">
                                <div class="genre">Romance</div>
                                <strong class="title">Lore Olympus</strong>
                                <div class="author">Rachel Smythe</div>
                            </div>
                        </a>
                    </li>
                </ul>
            </body>
            </html>
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(200).body(html).build())

        val page = source.getPopular(1)
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(recorded.url.toString().contains("/en/ranking/trending"))

        assertEquals(2, page.items.size)
        assertFalse(page.hasNext)

        val first = page.items[0]
        assertEquals("Tower of God", first.title)
        assertEquals("SIU", first.author)
        assertEquals(listOf("Action"), first.genres)
        assertEquals("https://webtoon-phinf.pstatic.net/cover95.jpg", first.coverUrl)
        assertEquals(ReadingDirection.Webtoon, first.readingDirection)

        val second = page.items[1]
        assertEquals("Lore Olympus", second.title)
        assertEquals("Rachel Smythe", second.author)
        assertEquals(listOf("Romance"), second.genres)
    }

    @Test
    fun `getLatest parses updated webtoons`() = runTest {
        val html = """
            <ul class="webtoon_list">
                <li>
                    <a href="/en/action/lookism/list?title_no=436" class="link">
                        <div class="image_wrap">
                            <img src="https://webtoon-phinf.pstatic.net/lookism.jpg" />
                        </div>
                        <strong class="title">Lookism</strong>
                        <div class="author">Taejun Pak</div>
                    </a>
                </li>
            </ul>
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(200).body(html).build())

        val page = source.getLatest(1)
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(recorded.url.toString().contains("/en/ranking/update"))

        assertEquals(1, page.items.size)
        assertEquals("Lookism", page.items[0].title)
    }

    @Test
    fun `search with keyword queries search endpoint`() = runTest {
        val html = """
            <ul class="webtoon_list">
                <li>
                    <a href="/en/action/tower/list?title_no=123" class="link _card_item">
                        <div class="image_wrap">
                            <img src="https://webtoon-phinf.pstatic.net/tower.jpg" />
                        </div>
                        <div class="info_text">
                            <strong class="title">Tower Adventure</strong>
                            <div class="author">Creator</div>
                        </div>
                    </a>
                </li>
            </ul>
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(200).body(html).build())

        val page = source.search("tower", FilterList(), 1)
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(recorded.url.toString().contains("/en/search?keyword=tower"))

        assertEquals(1, page.items.size)
        assertEquals("Tower Adventure", page.items[0].title)
    }

    @Test
    fun `search with genre filter queries genre endpoint`() = runTest {
        val html = """
            <ul class="webtoon_list">
                <li>
                    <a href="/en/action/eleceed/list?title_no=1571" class="link">
                        <strong class="title">Eleceed</strong>
                    </a>
                </li>
            </ul>
            <div class="paginate">
                <a href="#" class="pg_next">Next</a>
            </div>
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(200).body(html).build())

        val filters = FilterList(Filter.Select("Genre", WebtoonsSource.GENRES.map { it.first }, state = 1)) // Action
        val page = source.search("", filters, 1)
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(recorded.url.toString().contains("/en/genres/action?page=1"))

        assertEquals(1, page.items.size)
        assertEquals("Eleceed", page.items[0].title)
        assertTrue(page.hasNext)
    }

    @Test
    fun `getDetails parses title description and author`() = runTest {
        val html = """
            <div class="detail_header">
                <div class="thmb">
                    <img src="https://webtoon-phinf.pstatic.net/header.jpg" />
                </div>
                <h1 class="subj">Omniscient Reader</h1>
                <div class="author">singNsong / Sleepy-C</div>
                <div class="genre">Action</div>
                <div class="genre">Fantasy</div>
                <p class="desc">What if your favorite novel became reality?</p>
            </div>
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(200).body(html).build())

        val manga = SManga(
            url = "/en/action/omniscient-reader/list?title_no=2154",
            title = "Omniscient Reader",
            coverUrl = null,
        )
        val details = source.getDetails(manga)

        assertEquals("Omniscient Reader", details.title)
        assertEquals("singNsong / Sleepy-C", details.author)
        assertEquals("What if your favorite novel became reality?", details.description)
        assertEquals(listOf("Action", "Fantasy"), details.genres)
        assertEquals("https://webtoon-phinf.pstatic.net/header.jpg", details.coverUrl)
        assertEquals(ReadingDirection.Webtoon, details.readingDirection)
        assertEquals(SManga.Status.Ongoing, details.status)
    }

    @Test
    fun `getChapters collects episodes across pages and orders them ascending`() = runTest {
        // Page 1 has newer episodes (2 and 3)
        val page1Html = """
            <ul id="_listUl" class="detail_list">
                <li class="_episodeItem" data-episode-no="3">
                    <a href="/en/action/test/ep3/viewer?title_no=1&episode_no=3" class="detail_list_link">
                        <span class="subj"><span>Episode 3</span></span>
                        <span class="date">Oct 15, 2026</span>
                    </a>
                </li>
                <li class="_episodeItem" data-episode-no="2">
                    <a href="/en/action/test/ep2/viewer?title_no=1&episode_no=2" class="detail_list_link">
                        <span class="subj"><span>Episode 2</span></span>
                        <span class="date">Oct 8, 2026</span>
                    </a>
                </li>
            </ul>
            <div class="paginate">
                <a href="#" class="pg_next">Next</a>
            </div>
        """.trimIndent()

        // Page 2 has episode 1 (oldest) and no next page
        val page2Html = """
            <ul id="_listUl" class="detail_list">
                <li class="_episodeItem" data-episode-no="1">
                    <a href="/en/action/test/ep1/viewer?title_no=1&episode_no=1" class="detail_list_link">
                        <span class="subj"><span>Episode 1</span></span>
                        <span class="date">Oct 1, 2026</span>
                    </a>
                </li>
            </ul>
            <div class="paginate">
                <span class="pg_next off">Next</span>
            </div>
        """.trimIndent()

        server.enqueue(MockResponse.Builder().code(200).body(page1Html).build())
        server.enqueue(MockResponse.Builder().code(200).body(page2Html).build())

        val manga = SManga(url = "/en/action/test/list?title_no=1", title = "Test Manga", coverUrl = null)
        val chapters = source.getChapters(manga)

        assertEquals(3, chapters.size)
        // Ordered ascending by chapter number: 1, 2, 3
        assertEquals(1f, chapters[0].number)
        assertEquals("Episode 1", chapters[0].name)
        assertEquals(2f, chapters[1].number)
        assertEquals(3f, chapters[2].number)

        val expectedDate = LocalDate.parse("2026-10-01")
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        assertEquals(expectedDate, chapters[0].uploadedAt)
    }

    @Test
    fun `getPages extracts images and skips transparency placeholder`() = runTest {
        val html = """
            <div id="_imageList" class="viewer_img">
                <img src="https://webtoons-static.pstatic.net/image/bg_transparency.png"
                     data-url="https://webtoon-phinf.pstatic.net/page1.jpg" />
                <img src="https://webtoons-static.pstatic.net/image/bg_transparency.png"
                     data-url="https://webtoon-phinf.pstatic.net/page2.jpg" />
                <img src="https://webtoons-static.pstatic.net/image/bg_transparency.png" />
            </div>
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(200).body(html).build())

        val chapter = SChapter(url = "/en/action/test/ep1/viewer?title_no=1&episode_no=1", name = "Ep 1", number = 1f)
        val pages = source.getPages(chapter)

        assertEquals(2, pages.size)
        val p1 = pages[0] as app.tsunayomi.source.api.SPage.Remote
        val p2 = pages[1] as app.tsunayomi.source.api.SPage.Remote
        assertEquals("https://webtoon-phinf.pstatic.net/page1.jpg", p1.imageUrl)
        assertEquals("https://webtoon-phinf.pstatic.net/page2.jpg", p2.imageUrl)
    }

    @Test
    fun `WebtoonsInterceptor attaches Referer header to matching hosts`() {
        val interceptor = WebtoonsInterceptor()
        val request = Request.Builder().url("https://webtoon-phinf.pstatic.net/img.jpg").build()
        val captured = slot<Request>()
        val chain = mockk<okhttp3.Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(capture(captured)) } returns mockk(relaxed = true)

        interceptor.intercept(chain)
        assertEquals("https://www.webtoons.com/", captured.captured.header("Referer"))
    }

    @Test
    fun `WebtoonsInterceptor does not attach Referer to other hosts`() {
        val interceptor = WebtoonsInterceptor()
        val request = Request.Builder().url("https://example.com/img.jpg").build()
        val captured = slot<Request>()
        val chain = mockk<okhttp3.Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(capture(captured)) } returns mockk(relaxed = true)

        interceptor.intercept(chain)
        assertEquals(null, captured.captured.header("Referer"))
    }

    @Test
    fun `parseDate handles valid English month day year string`() {
        val parsed = source.parseDate("Oct 23, 2026")
        assertNotNull(parsed)
        val expected = LocalDate.of(2026, 10, 23)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, parsed)
    }
}

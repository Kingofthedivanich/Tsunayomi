package app.tsunayomi.source.naver

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

class NaverSourceTest {
    private val server = MockWebServer()
    private lateinit var source: NaverSource
    private val client = OkHttpClient()

    @BeforeEach
    fun setUp() {
        server.start()
        source = NaverSource(client, baseUrl = server.url("").toString().removeSuffix("/"))
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `getPopular parses weekday webtoons`() = runTest {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="area_toon">
                    <a href="/webtoon/list?titleId=183559&week=mon" class="link_thumbnail">
                        <div class="thumbnail">
                            <img src="https://image-comic.pstatic.net/thumb183559.jpg" />
                        </div>
                        <div class="link_info">
                            <strong class="title">신의 탑</strong>
                            <span class="author">SIU</span>
                            <p class="summary">자신의 모든 것이었던 소녀를 쫓아 탑에 들어온 소년.</p>
                        </div>
                    </a>
                </div>
            </body>
            </html>
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(200).body(html).build())

        val page = source.getPopular(1)
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(recorded.url.toString().contains("/webtoon/weekday"))

        assertEquals(1, page.items.size)
        assertFalse(page.hasNext)

        val item = page.items[0]
        assertEquals("신의 탑", item.title)
        assertEquals("SIU", item.author)
        assertEquals("https://image-comic.pstatic.net/thumb183559.jpg", item.coverUrl)
        assertEquals("ko", item.originalLang)
        assertEquals(ReadingDirection.Webtoon, item.readingDirection)
    }

    @Test
    fun `getLatest parses update sorted webtoons`() = runTest {
        val html = """
            <a href="/webtoon/list?titleId=641253" class="link_thumbnail">
                <div class="thumbnail">
                    <img src="https://image-comic.pstatic.net/thumb641253.jpg" />
                </div>
                <div class="link_info">
                    <strong class="title">외모지상주의</strong>
                    <span class="author">박태준</span>
                </div>
            </a>
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(200).body(html).build())

        val page = source.getLatest(1)
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(recorded.url.toString().contains("/webtoon/weekday?sort=UPDATE"))

        assertEquals(1, page.items.size)
        assertEquals("외모지상주의", page.items[0].title)
    }

    @Test
    fun `search with keyword parses search results`() = runTest {
        val html = """
            <article class="result_lst">
                <ul class="toon_lst">
                    <li>
                        <div class="lst">
                            <a href="/webtoon/list?titleId=855151">
                                <span class="im_br">
                                    <img src="https://image-comic.pstatic.net/thumb855151.jpg" alt="신들의 단톡방" />
                                </span>
                                <div class="toon_info">
                                    <strong class="title">신들의 단톡방</strong>
                                    <span class="author">작가명</span>
                                </div>
                            </a>
                        </div>
                    </li>
                </ul>
            </article>
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(200).body(html).build())

        val page = source.search("신들의", FilterList(), 1)
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(recorded.url.toString().contains("/search/result?keyword="))

        assertEquals(1, page.items.size)
        assertEquals("신들의 단톡방", page.items[0].title)
        assertEquals("https://image-comic.pstatic.net/thumb855151.jpg", page.items[0].coverUrl)
    }

    @Test
    fun `search with day filter queries specific weekday`() = runTest {
        val html = """
            <a href="/webtoon/list?titleId=123&week=wed" class="link_thumbnail">
                <strong class="title">수요웹툰</strong>
            </a>
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(200).body(html).build())

        val filters = FilterList(Filter.Select("Day", NaverSource.WEEKDAYS.map { it.first }, state = 3)) // Wed
        val page = source.search("", filters, 1)
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(recorded.url.toString().contains("/webtoon/weekday?week=wed"))

        assertEquals(1, page.items.size)
        assertEquals("수요웹툰", page.items[0].title)
    }

    @Test
    fun `getDetails parses title description author and cover`() = runTest {
        val html = """
            <div class="title_box">
                <h2 class="title">전지적 독자 시점</h2>
            </div>
            <div class="link_info">
                <span class="author">싱숑 / UMI / 슬리피-C</span>
                <p class="summary">오직 나만이, 이 세계의 결말을 알고 있다.</p>
            </div>
            <div class="thumbnail">
                <img src="https://image-comic.pstatic.net/omniscient.jpg" />
            </div>
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(200).body(html).build())

        val manga = SManga(url = "/webtoon/list?titleId=747269", title = "전지적 독자 시점", coverUrl = null)
        val details = source.getDetails(manga)

        assertEquals("전지적 독자 시점", details.title)
        assertEquals("싱숑 / UMI / 슬리피-C", details.author)
        assertEquals("오직 나만이, 이 세계의 결말을 알고 있다.", details.description)
        assertEquals("https://image-comic.pstatic.net/omniscient.jpg", details.coverUrl)
        assertEquals("ko", details.originalLang)
        assertEquals(ReadingDirection.Webtoon, details.readingDirection)
        assertEquals(SManga.Status.Ongoing, details.status)
    }

    @Test
    fun `getChapters collects episodes and orders them ascending`() = runTest {
        val html = """
            <ul class="list_toon">
                <li class="item" data-title-id="100" data-no="2">
                    <a href="/webtoon/detail?titleId=100&no=2" class="link">
                        <div class="info">
                            <span class="name"><strong>2화</strong></span>
                            <span class="date">26.10.23</span>
                        </div>
                    </a>
                </li>
                <li class="item" data-title-id="100" data-no="1">
                    <a href="/webtoon/detail?titleId=100&no=1" class="link">
                        <div class="info">
                            <span class="name"><strong>1화</strong></span>
                            <span class="date">26.10.16</span>
                        </div>
                    </a>
                </li>
            </ul>
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(200).body(html).build())

        val manga = SManga(url = "/webtoon/list?titleId=100", title = "Test Manga", coverUrl = null)
        val chapters = source.getChapters(manga)

        assertEquals(2, chapters.size)
        // Ordered ascending: episode 1, then 2
        assertEquals(1f, chapters[0].number)
        assertEquals("1화", chapters[0].name)
        assertEquals(2f, chapters[1].number)
        assertEquals("2화", chapters[1].name)

        val expectedDate = LocalDate.of(2026, 10, 16)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        assertEquals(expectedDate, chapters[0].uploadedAt)
    }

    @Test
    fun `getPages extracts image URLs and filters out placeholder`() = runTest {
        val html = """
            <div id="toonLayer" class="toon_view_lst">
                <img src="https://static.naver.com/icon_trans.gif"
                     data-src="https://image-comic.pstatic.net/mobilewebimg/100/1/001.jpg" />
                <img src="https://static.naver.com/icon_trans.gif"
                     data-src="https://image-comic.pstatic.net/mobilewebimg/100/1/002.jpg" />
                <img src="https://static.naver.com/icon_trans.gif" />
            </div>
        """.trimIndent()
        server.enqueue(MockResponse.Builder().code(200).body(html).build())

        val chapter = SChapter(url = "/webtoon/detail?titleId=100&no=1", name = "1화", number = 1f)
        val pages = source.getPages(chapter)

        assertEquals(2, pages.size)
        val p1 = pages[0] as app.tsunayomi.source.api.SPage.Remote
        val p2 = pages[1] as app.tsunayomi.source.api.SPage.Remote
        assertEquals("https://image-comic.pstatic.net/mobilewebimg/100/1/001.jpg", p1.imageUrl)
        assertEquals("https://image-comic.pstatic.net/mobilewebimg/100/1/002.jpg", p2.imageUrl)
    }

    @Test
    fun `NaverInterceptor attaches Referer header to image-comic and naver hosts`() {
        val interceptor = NaverInterceptor()
        val request = Request.Builder().url("https://image-comic.pstatic.net/img.jpg").build()
        val captured = slot<Request>()
        val chain = mockk<okhttp3.Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(capture(captured)) } returns mockk(relaxed = true)

        interceptor.intercept(chain)
        assertEquals("https://m.comic.naver.com/", captured.captured.header("Referer"))
    }

    @Test
    fun `parseDate handles short and long Korean dates`() {
        val shortDate = source.parseDate("26.10.23")
        assertNotNull(shortDate)
        val expected = LocalDate.of(2026, 10, 23)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, shortDate)

        val longDate = source.parseDate("2026.10.23")
        assertNotNull(longDate)
        assertEquals(expected, longDate)
    }
}

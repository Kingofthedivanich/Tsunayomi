package app.tsunayomi.source.naver

import app.tsunayomi.source.api.Filter
import app.tsunayomi.source.api.FilterList
import app.tsunayomi.source.api.ReadingDirection
import app.tsunayomi.source.api.SChapter
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SPage
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NaverSourceTest {
    private val server = MockWebServer()
    private lateinit var source: NaverSource
    private val requests = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val target = request.target
                requests += target
                val body = when {
                    target.startsWith("/api/webtoon/titlelist/weekday") -> WEEKDAY
                    target.startsWith("/api/search/all") -> SEARCH
                    target.startsWith("/api/article/list/info") -> INFO
                    target.startsWith("/api/article/list") && "page=1" in target -> ARTICLES_PAGE_1
                    target.startsWith("/api/article/list") -> ARTICLES_PAGE_2
                    target.startsWith("/webtoon/detail") -> VIEWER
                    else -> return MockResponse.Builder().code(404).build()
                }
                return MockResponse.Builder().body(body).build()
            }
        }
        server.start()
        source = NaverSource(OkHttpClient(), baseUrl = server.url("").toString().removeSuffix("/"))
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `popular lists every day once and skips adult titles`() = runTest {
        val page = source.getPopular(1)

        assertEquals(listOf("신의 탑", "전지적 독자 시점"), page.items.map { it.title })
        assertEquals("/webtoon/list?titleId=183559", page.items[0].url)
        assertEquals("ko", page.items[0].originalLang)
        assertEquals(ReadingDirection.Webtoon, page.items[0].readingDirection)
        assertEquals(false, page.hasNext)
    }

    @Test
    fun `day filter picks one day`() = runTest {
        val filters = source.getFilters()
        (filters.first() as Filter.Select).state = 3 // Wed

        val page = source.search("", filters, 1)

        assertEquals(listOf("전지적 독자 시점"), page.items.map { it.title })
    }

    @Test
    fun `search keeps only regular webtoons`() = runTest {
        val page = source.search("독자", FilterList(), 1)

        assertEquals(listOf("전지적 독자 시점"), page.items.map { it.title })
        assert(requests.any { it.startsWith("/api/search/all?keyword=") })
    }

    @Test
    fun `details come from the title info`() = runTest {
        val manga = source.getDetails(SManga(url = "/webtoon/list?titleId=747269", title = "", coverUrl = null))

        assertEquals("전지적 독자 시점", manga.title)
        assertEquals("UMI", manga.author)
        assertEquals("슬리피-C", manga.artist)
        assertEquals(listOf("판타지"), manga.genres)
        assertEquals(SManga.Status.Ongoing, manga.status)
    }

    @Test
    fun `chapters are read from every page, paid ones dropped, oldest first`() = runTest {
        val chapters = source.getChapters(SManga(url = "/webtoon/list?titleId=747269", title = "", coverUrl = null))

        assertEquals(listOf(1f, 2f, 3f), chapters.map { it.number })
        assertEquals("/webtoon/detail?titleId=747269&no=1", chapters[0].url)
        assertEquals("000. 프롤로그", chapters[0].name)
        assertEquals(source.parseDate("26.06.09"), chapters[2].uploadedAt)
    }

    @Test
    fun `pages are the viewer images with a referer`() = runTest {
        val pages = source.getPages(SChapter(url = "/webtoon/detail?titleId=747269&no=3", name = "", number = 3f))
            .map { it as SPage.Remote }

        assertEquals(
            listOf(
                "https://image-comic.pstatic.net/webtoon/747269/3/a_IMAG01_1.jpg",
                "https://image-comic.pstatic.net/webtoon/747269/3/a_IMAG01_2.jpg",
            ),
            pages.map { it.imageUrl },
        )
        assertEquals(server.url("/").toString(), pages[0].headers["Referer"])
    }

    private companion object {
        val WEEKDAY = """
            {"titleListMap":{
              "MONDAY":[{"titleId":183559,"titleName":"신의 탑","author":"SIU","thumbnailUrl":"https://img/tower.jpg","adult":false},
                        {"titleId":1,"titleName":"성인","adult":true}],
              "WEDNESDAY":[{"titleId":747269,"titleName":"전지적 독자 시점","author":"UMI","adult":false}],
              "SUNDAY":[{"titleId":183559,"titleName":"신의 탑","adult":false}]
            },"dayOfWeek":"MONDAY"}
        """.trimIndent()

        val SEARCH = """
            {"searchWebtoonResult":{"searchViewList":[
              {"titleId":747269,"titleName":"전지적 독자 시점","webtoonLevelCode":"WEBTOON","displayAuthor":"UMI / 슬리피-C","adult":false},
              {"titleId":50300,"titleName":"커피중독자","webtoonLevelCode":"BEST_CHALLENGE","adult":false}
            ]}}
        """.trimIndent()

        val INFO = """
            {"titleId":747269,"titleName":"전지적 독자 시점","thumbnailUrl":"https://img/orv.jpg","synopsis":"설명","finished":false,
             "communityArtists":[{"name":"UMI","artistTypeList":["ARTIST_WRITER"]},{"name":"슬리피-C","artistTypeList":["ARTIST_PAINTER"]}],
             "curationTagList":[{"tagName":"판타지","curationType":"GENRE_FANTASY"},{"tagName":"게임","curationType":"CUSTOM_TAG"}]}
        """.trimIndent()

        val ARTICLES_PAGE_1 = """
            {"articleList":[{"no":4,"subtitle":"유료","charge":true},{"no":3,"subtitle":"3화","serviceDateDescription":"26.06.09"},
                            {"no":2,"subtitle":"2화"}],
             "pageInfo":{"totalPages":2}}
        """.trimIndent()

        val ARTICLES_PAGE_2 = """
            {"articleList":[{"no":1,"subtitle":"000. 프롤로그"}],"pageInfo":{"totalPages":2}}
        """.trimIndent()

        val VIEWER = """
            <html><body>
              <img src="https://image-comic.pstatic.net/webtoon/747269/3/thumbnail_202x120.jpg">
              <div class="wt_viewer" id="sectionContWide">
                <img src="https://image-comic.pstatic.net/webtoon/747269/3/a_IMAG01_1.jpg" alt="comic content">
                <img src="https://image-comic.pstatic.net/webtoon/747269/3/a_IMAG01_2.jpg" alt="comic content">
              </div>
            </body></html>
        """.trimIndent()
    }
}

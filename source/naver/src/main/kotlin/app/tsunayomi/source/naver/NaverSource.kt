package app.tsunayomi.source.naver

import app.tsunayomi.core.network.RateLimitInterceptor
import app.tsunayomi.core.network.await
import app.tsunayomi.core.network.bodyOrThrow
import app.tsunayomi.source.api.CatalogSource
import app.tsunayomi.source.api.Filter
import app.tsunayomi.source.api.FilterList
import app.tsunayomi.source.api.MangaPage
import app.tsunayomi.source.api.ReadingDirection
import app.tsunayomi.source.api.SChapter
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SPage
import app.tsunayomi.source.api.sourceIdOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Official Korean Naver Webtoon (comic.naver.com): the original Korean webtoons, for reading with translation.
 * The site is a web app, so lists come from the JSON API it uses itself; only the viewer page is HTML.
 * Paid ("charge") episodes and adult titles, which need a Naver account, are left out.
 */
class NaverSource(
    client: OkHttpClient,
    private val baseUrl: String = BASE_URL,
) : CatalogSource {
    override val name: String = "Naver Webtoon"
    override val lang: String = "ko"
    override val id: Long = sourceIdOf(name, lang)
    override val supportsLatest: Boolean = true

    private val apiClient = client.newBuilder()
        .addInterceptor(RateLimitInterceptor(permits = 4, period = 1))
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override fun getFilters(): FilterList = FilterList(
        Filter.Select("Day", WEEKDAYS.map { it.first }, state = 0),
    )

    override suspend fun getPopular(page: Int): MangaPage = weekday(order = "user", day = null, page = page)

    override suspend fun getLatest(page: Int): MangaPage = weekday(order = "update", day = null, page = page)

    override suspend fun search(query: String, filters: FilterList, page: Int): MangaPage {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            val dayIndex = filters.filterIsInstance<Filter.Select>().firstOrNull()?.state ?: 0
            return weekday(order = "user", day = WEEKDAYS.getOrNull(dayIndex)?.second, page = page)
        }
        if (page > 1) return MangaPage(emptyList(), hasNext = false)
        val url = api("search", "all").addQueryParameter("keyword", trimmed).build()
        val result = json.decodeFromString<NaverSearchAll>(get(url))
        val items = result.searchWebtoonResult?.searchViewList.orEmpty()
            .filter { !it.adult && it.webtoonLevelCode == "WEBTOON" }
            .map { it.toSManga() }
        return MangaPage(items, hasNext = false)
    }

    override suspend fun getDetails(manga: SManga): SManga {
        val info = json.decodeFromString<NaverTitleInfo>(
            get(api("article", "list", "info").addQueryParameter("titleId", titleId(manga.url)).build()),
        )
        val authors = info.communityArtists
        return manga.copy(
            title = info.titleName,
            coverUrl = info.thumbnailUrl ?: manga.coverUrl,
            author = authors.filter { "ARTIST_WRITER" in it.artistTypeList }.joinToString(", ") { it.name }
                .ifEmpty { manga.author },
            artist = authors.filter { "ARTIST_PAINTER" in it.artistTypeList }.joinToString(", ") { it.name }
                .ifEmpty { manga.artist },
            description = info.synopsis ?: manga.description,
            genres = info.curationTagList.filter { it.curationType?.startsWith("GENRE") == true }.map { it.tagName }
                .ifEmpty { manga.genres },
            status = if (info.finished) SManga.Status.Completed else SManga.Status.Ongoing,
            readingDirection = ReadingDirection.Webtoon,
            originalLang = "ko",
        )
    }

    override suspend fun getChapters(manga: SManga): List<SChapter> {
        val titleId = titleId(manga.url)
        val chapters = mutableListOf<SChapter>()
        var page = 1
        while (page <= MAX_CHAPTER_PAGES) {
            val url = api("article", "list")
                .addQueryParameter("titleId", titleId)
                .addQueryParameter("page", page.toString())
                .build()
            val list = json.decodeFromString<NaverArticleList>(get(url))
            list.articleList.filterNot { it.charge }.mapTo(chapters) { article ->
                SChapter(
                    url = "/webtoon/detail?titleId=$titleId&no=${article.no}",
                    name = article.subtitle.ifBlank { "${article.no}화" },
                    number = article.no.toFloat(),
                    uploadedAt = article.serviceDateDescription?.let(::parseDate),
                )
            }
            if (list.articleList.isEmpty() || page >= (list.pageInfo?.totalPages ?: 1)) break
            page++
        }
        // The API lists the newest episode first; the app wants reading order.
        return chapters.distinctBy { it.url }.sortedBy { it.number }
    }

    override suspend fun getPages(chapter: SChapter): List<SPage> {
        val url = absolute(chapter.url)
        val doc = Jsoup.parse(get(url.toHttpUrl()), url)
        return doc.select("#sectionContWide img")
            .map { it.absUrl("src").ifEmpty { it.absUrl("data-src") } }
            .filter { it.isNotBlank() && "/webtoon/" in it && "thumbnail" !in it }
            .distinct()
            .mapIndexed { index, imageUrl ->
                SPage.Remote(index = index, imageUrl = imageUrl, headers = mapOf("Referer" to "$baseUrl/"))
            }
    }

    private suspend fun weekday(order: String, day: String?, page: Int): MangaPage {
        // The whole week comes in one response.
        if (page > 1) return MangaPage(emptyList(), hasNext = false)
        val url = api("webtoon", "titlelist", "weekday").addQueryParameter("order", order).build()
        val map = json.decodeFromString<NaverWeekdayList>(get(url)).titleListMap
        val titles = if (day != null) {
            map[day].orEmpty()
        } else {
            DAY_KEYS.flatMap { map[it].orEmpty() }
        }
        val items = titles.filterNot { it.adult }.distinctBy { it.titleId }.map { it.toSManga() }
        return MangaPage(items, hasNext = false)
    }

    private fun NaverTitle.toSManga() = SManga(
        url = "/webtoon/list?titleId=$titleId",
        title = titleName,
        coverUrl = thumbnailUrl,
        author = displayAuthor ?: author,
        description = synopsis,
        status = if (finish || finished) SManga.Status.Completed else SManga.Status.Ongoing,
        readingDirection = ReadingDirection.Webtoon,
        originalLang = "ko",
    )

    private fun api(vararg segments: String): HttpUrl.Builder =
        "$baseUrl/api".toHttpUrl().newBuilder().apply { segments.forEach(::addPathSegment) }

    private fun absolute(url: String) = if (url.startsWith("http")) url else "$baseUrl$url"

    private fun titleId(url: String): String =
        absolute(url).toHttpUrl().queryParameter("titleId") ?: error("No titleId in $url")

    // Reading the body happens on the calling thread, so keep it off the main one.
    private suspend fun get(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$baseUrl/")
            .build()
        apiClient.newCall(request).await().bodyOrThrow()
    }

    internal fun parseDate(dateStr: String): Long? = runCatching {
        LocalDate.parse(dateStr.trim(), DATE_FORMAT).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrNull()

    companion object {
        const val BASE_URL = "https://comic.naver.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36"
        private const val MAX_CHAPTER_PAGES = 100
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yy.MM.dd")

        private val DAY_KEYS = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")

        val WEEKDAYS = listOf(
            "All" to null,
            "Mon" to "MONDAY",
            "Tue" to "TUESDAY",
            "Wed" to "WEDNESDAY",
            "Thu" to "THURSDAY",
            "Fri" to "FRIDAY",
            "Sat" to "SATURDAY",
            "Sun" to "SUNDAY",
        )
    }
}

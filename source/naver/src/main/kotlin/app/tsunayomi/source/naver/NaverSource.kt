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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Official Korean Naver Webtoon (comic.naver.com / m.comic.naver.com).
 * Original Korean RAW webtoons, ideal for real-time auto-translation into Russian and English.
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

    override fun getFilters(): FilterList = FilterList(
        Filter.Select("Day", WEEKDAYS.map { it.first }, state = 0),
    )

    override suspend fun getPopular(page: Int): MangaPage {
        if (page > 1) return MangaPage(emptyList(), hasNext = false)
        val url = "$baseUrl/webtoon/weekday"
        val doc = fetchHtml(url)
        val items = parseMangaList(doc)
        return MangaPage(items, hasNext = false)
    }

    override suspend fun getLatest(page: Int): MangaPage {
        if (page > 1) return MangaPage(emptyList(), hasNext = false)
        val url = "$baseUrl/webtoon/weekday?sort=UPDATE"
        val doc = fetchHtml(url)
        val items = parseMangaList(doc)
        return MangaPage(items, hasNext = false)
    }

    override suspend fun search(query: String, filters: FilterList, page: Int): MangaPage {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "$baseUrl/search/result?keyword=$encoded"
            val doc = fetchHtml(url)
            val items = parseSearchResults(doc)
            return MangaPage(items, hasNext = false)
        }

        val dayFilter = filters.filterIsInstance<Filter.Select>().firstOrNull()
        val dayIndex = dayFilter?.state ?: 0
        val dayCode = WEEKDAYS.getOrNull(dayIndex)?.second

        val url = if (!dayCode.isNullOrEmpty()) {
            "$baseUrl/webtoon/weekday?week=$dayCode"
        } else {
            "$baseUrl/webtoon/weekday"
        }
        val doc = fetchHtml(url)
        val items = parseMangaList(doc)
        return MangaPage(items, hasNext = false)
    }

    override suspend fun getDetails(manga: SManga): SManga {
        val url = normalizeUrl(manga.url)
        val doc = fetchHtml(url)

        val title = doc.select(".title_box .title, h2.title, strong.title, .tit").text().trim().ifEmpty { manga.title }
        val author = doc.select(".link_info .author, span.author, .author").text().trim().ifEmpty { manga.author }
        val description = doc.select("p.summary, .summary, .desc").text().trim().ifEmpty { manga.description }
        val coverUrl = doc.select(".thumbnail img, .thumb img").attr("src").ifEmpty { manga.coverUrl }
        val isCompleted = doc.select(".badge_completed, .ico_break").isNotEmpty()

        return manga.copy(
            title = title,
            author = author,
            description = description,
            coverUrl = coverUrl,
            status = if (isCompleted) SManga.Status.Completed else SManga.Status.Ongoing,
            readingDirection = ReadingDirection.Webtoon,
            originalLang = "ko",
        )
    }

    override suspend fun getChapters(manga: SManga): List<SChapter> {
        val base = normalizeUrl(manga.url)
        val chapters = mutableListOf<SChapter>()
        val seenUrls = mutableSetOf<String>()
        var page = 1

        while (page <= MAX_CHAPTER_PAGES) {
            val pageUrl = if (base.contains("?")) "$base&page=$page" else "$base?page=$page"
            val doc = fetchHtml(pageUrl)
            val items = doc.select("ul.list_toon li.item, ul.detail_list li.item, li.item[data-no]")
            if (items.isEmpty()) break

            var newItemsFound = false
            for (element in items) {
                val link = element.select("a.link, a").attr("href").trim()
                if (link.isEmpty() || link in seenUrls) continue
                seenUrls += link
                newItemsFound = true

                val name = element.select(".name strong, .info .name, strong.title, .subj")
                    .firstOrNull()?.text()?.trim().orEmpty()
                val number = element.attr("data-no").toFloatOrNull()
                    ?: parseEpisodeNumberFromUrl(link)
                    ?: 0f
                val dateStr = element.select(".date").text().trim()
                val uploadedAt = parseDate(dateStr)

                chapters += SChapter(
                    url = link,
                    name = name.ifEmpty { "Episode $number" },
                    number = number,
                    uploadedAt = uploadedAt,
                )
            }

            if (!newItemsFound) break

            val hasNextPage = doc.select(".paginate a.next, .btn_next").isNotEmpty() &&
                doc.select(".paginate a.next.off, .btn_next.off").isEmpty()
            if (!hasNextPage && items.size < 10) break

            page++
        }

        return chapters.sortedBy { it.number }
    }

    override suspend fun getPages(chapter: SChapter): List<SPage> {
        val url = normalizeUrl(chapter.url)
        val doc = fetchHtml(url)
        val imageElements = doc.select("div.toon_view_lst img, div#toonLayer img, div.ct img")

        return imageElements.mapNotNull { img ->
            val imgUrl = img.attr("data-src").ifEmpty { img.attr("src") }.trim()
            if (imgUrl.isBlank() || imgUrl.contains("icon_trans.gif") || imgUrl.contains("bg_transparency.png")) null else imgUrl
        }.distinct().mapIndexed { index, imageUrl ->
            SPage.Remote(
                index = index,
                imageUrl = imageUrl,
                headers = mapOf("Referer" to "$baseUrl/"),
            )
        }
    }

    private suspend fun fetchHtml(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$baseUrl/")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()
        val html = apiClient.newCall(request).await().bodyOrThrow()
        return Jsoup.parse(html, url)
    }

    private fun parseMangaList(doc: Document): List<SManga> {
        val elements = doc.select("a.link_thumbnail, ul.list_toon li a, div.area_toon a.link_thumbnail")
        return elements.mapNotNull { el ->
            val link = el.attr("href").trim()
            if (link.isEmpty()) return@mapNotNull null

            val title = el.select("strong.title, .title").text().trim()
            if (title.isEmpty()) return@mapNotNull null

            val cover = el.select(".thumbnail img, img").attr("src").trim()
            val author = el.select(".author, span.author").text().trim().takeIf { it.isNotEmpty() }
            val summary = el.select("p.summary, .summary").text().trim().takeIf { it.isNotEmpty() }

            SManga(
                url = link,
                title = title,
                coverUrl = cover.ifEmpty { null },
                author = author,
                description = summary,
                genres = listOf("Webtoon"),
                status = SManga.Status.Ongoing,
                readingDirection = ReadingDirection.Webtoon,
                originalLang = "ko",
            )
        }.distinctBy { it.url }
    }

    private fun parseSearchResults(doc: Document): List<SManga> {
        val elements = doc.select("article.result_lst ul.toon_lst li a, ul.toon_lst li a, div.lst a")
        return elements.mapNotNull { el ->
            val link = el.attr("href").trim()
            if (link.isEmpty() || !link.contains("titleId=")) return@mapNotNull null

            val title = el.select(".toon_info strong.title, strong.title, .title").text().trim()
                .ifEmpty { el.select("img").attr("alt").trim() }
            if (title.isEmpty()) return@mapNotNull null

            val cover = el.select("img").attr("src").trim()
            val author = el.select(".author, span.author").text().trim().takeIf { it.isNotEmpty() }

            SManga(
                url = link,
                title = title,
                coverUrl = cover.ifEmpty { null },
                author = author,
                genres = listOf("Webtoon"),
                status = SManga.Status.Ongoing,
                readingDirection = ReadingDirection.Webtoon,
                originalLang = "ko",
            )
        }.distinctBy { it.url }
    }

    private fun normalizeUrl(url: String): String = if (url.startsWith("http")) url else "$baseUrl$url"

    private fun parseEpisodeNumberFromUrl(url: String): Float? = runCatching {
        val parsed = url.toHttpUrl()
        parsed.queryParameter("no")?.toFloatOrNull()
    }.getOrNull()

    internal fun parseDate(dateStr: String): Long? = runCatching {
        val trimmed = dateStr.trim()
        if (trimmed.isEmpty()) return null
        val formatter = if (trimmed.length <= 8) DATE_FORMAT_SHORT else DATE_FORMAT_LONG
        LocalDate.parse(trimmed, formatter)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

    companion object {
        const val BASE_URL = "https://m.comic.naver.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"
        private const val MAX_CHAPTER_PAGES = 50
        private val DATE_FORMAT_SHORT = DateTimeFormatter.ofPattern("yy.MM.dd", Locale.KOREAN)
        private val DATE_FORMAT_LONG = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREAN)

        val WEEKDAYS = listOf(
            "All" to "",
            "Mon" to "mon",
            "Tue" to "tue",
            "Wed" to "wed",
            "Thu" to "thu",
            "Fri" to "fri",
            "Sat" to "sat",
            "Sun" to "sun",
        )
    }
}

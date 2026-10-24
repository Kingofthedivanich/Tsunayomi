package app.tsunayomi.source.webtoons

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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Official LINE Webtoon (webtoons.com) catalog source.
 * Free official English webtoons (Tower of God, Lore Olympus, Lookism, Omniscient Reader, etc.).
 */
class WebtoonsSource(
    client: OkHttpClient,
    private val baseUrl: String = BASE_URL,
) : CatalogSource {
    override val name: String = "WEBTOON"
    override val lang: String = "en"
    override val id: Long = sourceIdOf(name, lang)
    override val supportsLatest: Boolean = true

    override fun webUrl(): String = baseUrl
    override fun webUrl(manga: SManga): String = normalizeUrl(manga.url)

    // Rate-limit to 4 req/s to remain a polite client and avoid rate-limiting.
    private val apiClient = client.newBuilder()
        .addInterceptor(RateLimitInterceptor(permits = 4, period = 1))
        .build()

    override fun getFilters(): FilterList = FilterList(
        Filter.Select("Genre", GENRES.map { it.first }, state = 0),
    )

    override suspend fun getPopular(page: Int): MangaPage {
        if (page > 1) return MangaPage(emptyList(), hasNext = false)
        val url = "$baseUrl/en/ranking/trending"
        val doc = fetchHtml(url)
        val items = parseMangaList(doc)
        return MangaPage(items, hasNext = false)
    }

    override suspend fun getLatest(page: Int): MangaPage {
        if (page > 1) return MangaPage(emptyList(), hasNext = false)
        val url = "$baseUrl/en/ranking/update"
        val doc = fetchHtml(url)
        val items = parseMangaList(doc)
        return MangaPage(items, hasNext = false)
    }

    override suspend fun search(query: String, filters: FilterList, page: Int): MangaPage {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "$baseUrl/en/search?keyword=$encoded"
            val doc = fetchHtml(url)
            val items = parseMangaList(doc)
            return MangaPage(items, hasNext = false)
        }

        val genreFilter = filters.filterIsInstance<Filter.Select>().firstOrNull()
        val genreIndex = genreFilter?.state ?: 0
        val genrePath = GENRES.getOrNull(genreIndex)?.second

        return if (!genrePath.isNullOrEmpty()) {
            val url = "$baseUrl/en/genres/$genrePath?page=$page"
            val doc = fetchHtml(url)
            val items = parseMangaList(doc)
            val hasNext = doc.select(".paginate a.pg_next").isNotEmpty() &&
                doc.select(".paginate span.pg_next.off").isEmpty()
            MangaPage(items, hasNext = hasNext)
        } else {
            getPopular(page)
        }
    }

    override suspend fun getDetails(manga: SManga): SManga {
        val url = normalizeUrl(manga.url)
        val doc = fetchHtml(url)

        val title = doc.select(".detail_header .subj, h1.subj, h1").text().trim().ifEmpty { manga.title }
        val author = doc.select(".detail_header .author, .author_area").text().trim().ifEmpty { manga.author }
        val description = doc.select(".detail_header p.desc, .detail_body .desc, p.desc").text().trim().ifEmpty { manga.description }
        val genres = doc.select(".detail_header .genre, .genre").eachText().map { it.trim() }.filter { it.isNotEmpty() }
        val coverUrl = doc.select(".detail_header .thmb img, div.image_wrap img").attr("src").ifEmpty { manga.coverUrl }
        val isCompleted = doc.select(".ico_completed").isNotEmpty() || doc.select(".badge_completed").isNotEmpty()

        return manga.copy(
            title = title,
            author = author,
            description = description,
            genres = if (genres.isNotEmpty()) genres else manga.genres,
            coverUrl = coverUrl,
            status = if (isCompleted) SManga.Status.Completed else SManga.Status.Ongoing,
            readingDirection = ReadingDirection.Webtoon,
            originalLang = "en",
        )
    }

    override suspend fun getChapters(manga: SManga): List<SChapter> {
        val baseUrl = normalizeUrl(manga.url)
        val chapters = mutableListOf<SChapter>()
        val seenUrls = mutableSetOf<String>()
        var page = 1

        while (page <= MAX_CHAPTER_PAGES) {
            val pageUrl = if (baseUrl.contains("?")) "$baseUrl&page=$page" else "$baseUrl?page=$page"
            val doc = fetchHtml(pageUrl)
            val items = doc.select("ul#_listUl li._episodeItem, li._episodeItem")
            if (items.isEmpty()) break

            var newItemsFound = false
            for (element in items) {
                val link = element.select("a.detail_list_link, a").attr("href").trim()
                if (link.isEmpty() || link in seenUrls) continue
                seenUrls += link
                newItemsFound = true

                val name = element.select(".subj span").firstOrNull()?.text()?.trim()
                    ?: element.select(".subj").text().trim()
                val number = element.attr("data-episode-no").toFloatOrNull()
                    ?: parseEpisodeNumberFromUrl(link)
                    ?: element.select(".tx").text().removePrefix("#").trim().toFloatOrNull()
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

            val hasNextPage = doc.select(".paginate a.pg_next").isNotEmpty() &&
                doc.select(".paginate span.pg_next.off").isEmpty()
            if (!hasNextPage && items.size < 10) break

            page++
        }

        // Return chapters in reading order: episode 1, 2, 3...
        return chapters.sortedBy { it.number }
    }

    override suspend fun getPages(chapter: SChapter): List<SPage> {
        val url = normalizeUrl(chapter.url)
        val doc = fetchHtml(url)
        val imageElements = doc.select("div#_imageList img, div.viewer_img img")

        return imageElements.mapNotNull { img ->
            val imgUrl = img.attr("data-url").ifEmpty { img.attr("src") }.trim()
            if (imgUrl.isBlank() || imgUrl.contains("bg_transparency.png")) null else imgUrl
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
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        val html = apiClient.newCall(request).await().bodyOrThrow()
        return Jsoup.parse(html, url)
    }

    private fun parseMangaList(doc: Document): List<SManga> {
        val elements = doc.select("ul.webtoon_list li a.link, ul.webtoon_list li a, div.card_item a")
        return elements.mapNotNull { el ->
            val link = el.attr("href").trim()
            if (link.isEmpty()) return@mapNotNull null

            val title = el.select(".title, strong.title").text().trim()
            if (title.isEmpty()) return@mapNotNull null

            val cover = el.select("img").attr("src").ifEmpty { el.select("img").attr("data-url") }.trim()
            val author = el.select(".author").text().trim().takeIf { it.isNotEmpty() }
            val genres = el.select(".genre").eachText().map { it.trim() }.filter { it.isNotEmpty() }

            SManga(
                url = link,
                title = title,
                coverUrl = cover.ifEmpty { null },
                author = author,
                genres = genres,
                status = SManga.Status.Ongoing,
                readingDirection = ReadingDirection.Webtoon,
                originalLang = "en",
            )
        }.distinctBy { it.url }
    }

    private fun normalizeUrl(url: String): String = if (url.startsWith("http")) url else "$baseUrl$url"

    private fun parseEpisodeNumberFromUrl(url: String): Float? = runCatching {
        val parsed = url.toHttpUrl()
        parsed.queryParameter("episode_no")?.toFloatOrNull()
    }.getOrNull()

    internal fun parseDate(dateStr: String): Long? = runCatching {
        if (dateStr.isBlank()) return null
        LocalDate.parse(dateStr, DATE_FORMAT)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

    companion object {
        const val BASE_URL = "https://www.webtoons.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val MAX_CHAPTER_PAGES = 50
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

        val GENRES = listOf(
            "All" to "",
            "Action" to "action",
            "Comedy" to "comedy",
            "Drama" to "drama",
            "Fantasy" to "fantasy",
            "Heartwarming" to "heartwarming",
            "Historical" to "historical",
            "Horror" to "horror",
            "Informative" to "informative",
            "Mystery" to "mystery",
            "Romance" to "romance",
            "Sci-Fi" to "sf",
            "Slice of Life" to "slice_of_life",
            "Sports" to "sports",
            "Super Hero" to "super_hero",
            "Supernatural" to "supernatural",
            "Thriller" to "thriller",
        )
    }
}

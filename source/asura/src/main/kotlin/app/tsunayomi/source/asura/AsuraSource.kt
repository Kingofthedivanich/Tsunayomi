package app.tsunayomi.source.asura

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
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.OffsetDateTime

/**
 * Built-in source for Asura Scans (asurascans.com), a popular English scanlation group
 * for manhwa and webtoons with a high-performance REST API.
 */
class AsuraSource(
    client: OkHttpClient,
    private val apiBaseUrl: HttpUrl = API_BASE_URL.toHttpUrl(),
) : CatalogSource {

    override val name: String = "Asura Scans"
    override val lang: String = "en"
    override val id: Long = sourceIdOf(name, lang)
    override val supportsLatest: Boolean = true

    private val apiClient = client.newBuilder()
        .addInterceptor(RateLimitInterceptor(permits = 5, period = 1))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override fun getFilters(): FilterList = AsuraFilters.create()

    override suspend fun getPopular(page: Int): MangaPage {
        val url = apiBaseUrl.newBuilder()
            .addPathSegment("series")
            .addQueryParameter("sort", "popular")
            .addQueryParameter("order", "desc")
            .addQueryParameter("page", page.toString())
            .build()
        return fetchSeriesPage(url)
    }

    override suspend fun getLatest(page: Int): MangaPage {
        val url = apiBaseUrl.newBuilder()
            .addPathSegment("series")
            .addQueryParameter("sort", "update")
            .addQueryParameter("order", "desc")
            .addQueryParameter("page", page.toString())
            .build()
        return fetchSeriesPage(url)
    }

    override suspend fun search(query: String, filters: FilterList, page: Int): MangaPage {
        val urlBuilder = apiBaseUrl.newBuilder()
            .addPathSegment("series")
            .addQueryParameter("page", page.toString())

        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            urlBuilder.addQueryParameter("search", trimmed)
        }

        var sortApplied = false
        filters.forEach { filter ->
            if (filter is Filter.Select) {
                when (filter.name) {
                    "Sort" -> {
                        val option = AsuraFilters.sortOptions.getOrNull(filter.state)?.second
                        if (option != null) {
                            urlBuilder.addQueryParameter("sort", option.first)
                            urlBuilder.addQueryParameter("order", option.second)
                            sortApplied = true
                        }
                    }
                    "Status" -> {
                        val status = AsuraFilters.statusOptions.getOrNull(filter.state)?.second
                        if (!status.isNullOrEmpty() && status != "all") {
                            urlBuilder.addQueryParameter("status", status)
                        }
                    }
                    "Type" -> {
                        val type = AsuraFilters.typeOptions.getOrNull(filter.state)?.second
                        if (!type.isNullOrEmpty() && type != "all") {
                            urlBuilder.addQueryParameter("type", type)
                        }
                    }
                }
            }
        }

        if (!sortApplied) {
            urlBuilder.addQueryParameter("sort", "popular")
            urlBuilder.addQueryParameter("order", "desc")
        }

        return fetchSeriesPage(urlBuilder.build())
    }

    private suspend fun fetchSeriesPage(url: HttpUrl): MangaPage {
        val body = get(url)
        val response = json.decodeFromString<AsuraSeriesListResponse>(body)
        val items = response.data.map { it.toSManga() }
        val hasNext = response.meta?.hasMore ?: (items.size >= 20)
        return MangaPage(items = items, hasNext = hasNext)
    }

    override suspend fun getDetails(manga: SManga): SManga {
        val url = apiBaseUrl.newBuilder()
            .addPathSegment("series")
            .addPathSegment(manga.url)
            .build()
        val body = get(url)
        val response = json.decodeFromString<AsuraSeriesDetailsResponse>(body)
        val series = response.series ?: return manga
        return series.toSManga()
    }

    override suspend fun getChapters(manga: SManga): List<SChapter> {
        val url = apiBaseUrl.newBuilder()
            .addPathSegment("series")
            .addPathSegment(manga.url)
            .addPathSegment("chapters")
            .build()
        val body = get(url)
        val response = json.decodeFromString<AsuraChaptersResponse>(body)

        return response.data
            .filterNot { it.isPremium }
            .map { chapter ->
                val numberString = if (chapter.number % 1f == 0f) {
                    chapter.number.toInt().toString()
                } else {
                    chapter.number.toString()
                }
                val titlePart = chapter.title?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""
                val chapterName = "Chapter $numberString$titlePart"
                val uploadedAt = parseDate(chapter.publishedAt)

                SChapter(
                    url = "${manga.url}/${chapter.slug}",
                    name = chapterName,
                    number = chapter.number,
                    uploadedAt = uploadedAt,
                    scanlator = "Asura Scans",
                    language = "en",
                )
            }
            // Reading order: the first chapter first.
            .sortedBy { it.number }
    }

    override suspend fun getPages(chapter: SChapter): List<SPage> {
        val parts = chapter.url.split("/")
        val seriesSlug = parts.getOrNull(0) ?: chapter.url
        val chapterSlug = parts.getOrNull(1) ?: chapter.url

        val url = apiBaseUrl.newBuilder()
            .addPathSegment("series")
            .addPathSegment(seriesSlug)
            .addPathSegment("chapters")
            .addPathSegment(chapterSlug)
            .build()
        val body = get(url)
        val response = json.decodeFromString<AsuraChapterDetailsResponse>(body)
        val pages = response.data?.chapter?.pages.orEmpty()

        return pages.mapIndexed { index, page ->
            SPage.Remote(index = index, imageUrl = page.url)
        }
    }

    private suspend fun get(url: HttpUrl): String =
        apiClient.newCall(Request.Builder().url(url).build()).await().bodyOrThrow()

    private fun AsuraSeriesItemDto.toSManga(): SManga {
        val statusEnum = when (status?.lowercase()) {
            "ongoing" -> SManga.Status.Ongoing
            "completed" -> SManga.Status.Completed
            "hiatus" -> SManga.Status.Hiatus
            else -> SManga.Status.Unknown
        }

        return SManga(
            url = slug,
            title = title,
            coverUrl = cover,
            author = author,
            artist = artist,
            description = description?.trim(),
            genres = genres.map { it.name },
            status = statusEnum,
            readingDirection = ReadingDirection.Webtoon,
            originalLang = "en",
        )
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return runCatching {
            OffsetDateTime.parse(dateStr).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }

    companion object {
        const val API_BASE_URL = "https://api.asurascans.com/api/"
    }
}

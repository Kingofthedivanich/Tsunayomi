package app.tsunayomi.source.mangadex

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
import java.util.concurrent.TimeUnit

/**
 * MangaDex through its public API (https://api.mangadex.org/docs). One instance per chapter language.
 * Terms: credit MangaDex and the scanlation groups, no ads or paid features.
 */
class MangaDexSource(
    override val lang: String,
    client: OkHttpClient,
    private val apiUrl: HttpUrl = API_URL.toHttpUrl(),
    private val coversUrl: String = COVERS_URL,
) : CatalogSource {
    override val name: String = "MangaDex"
    override val id: Long = sourceIdOf(name, lang)
    override val supportsLatest: Boolean = true

    // Stay under the documented ~5 req/s; the at-home endpoint has its own 40/min budget.
    private val apiClient = client.newBuilder().addInterceptor(RateLimitInterceptor(permits = 4, period = 1)).build()
    private val atHomeClient = apiClient.newBuilder()
        .addInterceptor(RateLimitInterceptor(permits = 40, period = 1, unit = TimeUnit.MINUTES))
        .build()

    override fun getFilters(): FilterList = MangaDexFilters.create()

    override suspend fun getPopular(page: Int): MangaPage = searchManga(page, query = null, filters = getFilters())

    override suspend fun getLatest(page: Int): MangaPage {
        val filters = getFilters()
        (filters.first { it is Filter.Sort } as Filter.Sort).state =
            Filter.Sort.Selection(MangaDexFilters.sortOptions.indexOfFirst { it.second == "latestUploadedChapter" }, ascending = false)
        return searchManga(page, query = null, filters = filters)
    }

    override suspend fun search(query: String, filters: FilterList, page: Int): MangaPage =
        searchManga(page, query.trim().ifEmpty { null }, filters.ifEmpty { getFilters() })

    private suspend fun searchManga(page: Int, query: String?, filters: List<Filter<*>>): MangaPage {
        val offset = (page - 1) * PAGE_SIZE
        val url = apiUrl.newBuilder().addPathSegment("manga").apply {
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("offset", offset.toString())
            addQueryParameter("includes[]", "cover_art")
            addQueryParameter("availableTranslatedLanguage[]", lang)
            addQueryParameter("hasAvailableChapters", "true")
            if (query != null) addQueryParameter("title", query)
            applyFilters(filters, hasQuery = query != null)
        }.build()
        val response = json.decodeFromString<MangaListResponse>(get(url))
        val nextOffset = response.offset + response.limit
        return MangaPage(
            items = response.data.map { it.toSManga(coverSize = 256) },
            hasNext = nextOffset < response.total && nextOffset + PAGE_SIZE <= MAX_OFFSET,
        )
    }

    private fun HttpUrl.Builder.applyFilters(filters: List<Filter<*>>, hasQuery: Boolean) {
        var ratingsAdded = false
        filters.forEach { filter ->
            when {
                filter is Filter.Sort -> {
                    val selection = filter.state ?: return@forEach
                    val key = MangaDexFilters.sortOptions.getOrNull(selection.index)?.second ?: return@forEach
                    // Relevance only makes sense for a text search.
                    if (key == "relevance" && !hasQuery) return@forEach
                    addQueryParameter("order[$key]", if (selection.ascending) "asc" else "desc")
                }
                filter is Filter.Select && filter.name == "Status" ->
                    MangaDexFilters.statusOptions.getOrNull(filter.state)?.second?.let { addQueryParameter("status[]", it) }
                filter is Filter.Select && filter.name == "Original language" ->
                    MangaDexFilters.originalLanguageOptions.getOrNull(filter.state)?.second
                        ?.let { addQueryParameter("originalLanguage[]", it) }
                filter is Filter.Group<*> && filter.name == "Content rating" -> {
                    filter.state.forEachIndexed { index, box ->
                        if ((box as? Filter.CheckBox)?.state == true) {
                            addQueryParameter("contentRating[]", MangaDexFilters.contentRatings[index].second)
                            ratingsAdded = true
                        }
                    }
                }
            }
        }
        if (!ratingsAdded) addQueryParameter("contentRating[]", "safe")
    }

    override suspend fun getDetails(manga: SManga): SManga {
        val url = apiUrl.newBuilder().addPathSegment("manga").addPathSegment(manga.url)
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("includes[]", "artist")
            .build()
        return json.decodeFromString<MangaResponse>(get(url)).data.toSManga(coverSize = 512)
    }

    override suspend fun getChapters(manga: SManga): List<SChapter> {
        val chapters = mutableListOf<ChapterDto>()
        var offset = 0
        do {
            val url = apiUrl.newBuilder().addPathSegment("manga").addPathSegment(manga.url).addPathSegment("feed")
                .addQueryParameter("translatedLanguage[]", lang)
                .addQueryParameter("limit", FEED_PAGE_SIZE.toString())
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("order[volume]", "asc")
                .addQueryParameter("order[chapter]", "asc")
                .addQueryParameter("includes[]", "scanlation_group")
                .addQueryParameter("includeExternalUrl", "0")
                .addQueryParameter("includeEmptyPages", "0")
                .apply { ALL_CONTENT_RATINGS.forEach { addQueryParameter("contentRating[]", it) } }
                .build()
            val response = json.decodeFromString<ChapterListResponse>(get(url))
            chapters += response.data
            offset = response.offset + response.limit
        } while (offset < response.total && offset < MAX_OFFSET)

        return chapters
            .filter { it.attributes.externalUrl == null && it.attributes.pages > 0 }
            .map { it.toSChapter() }
            // Volume-less chapters come last from the API; order everything by number, keeping group variants together.
            .sortedBy { chapter -> chapter.number.takeIf { it >= 0 } ?: Float.MAX_VALUE }
    }

    override suspend fun getPages(chapter: SChapter): List<SPage> {
        val url = apiUrl.newBuilder().addPathSegment("at-home").addPathSegment("server").addPathSegment(chapter.url).build()
        val response = json.decodeFromString<AtHomeResponse>(get(url, atHomeClient))
        // No auth headers on image requests: MangaDex@Home nodes are third parties.
        return response.chapter.data.mapIndexed { index, file ->
            SPage.Remote(index, "${response.baseUrl.trimEnd('/')}/data/${response.chapter.hash}/$file")
        }
    }

    private suspend fun get(url: HttpUrl, client: OkHttpClient = apiClient): String =
        client.newCall(Request.Builder().url(url).build()).await().bodyOrThrow()

    private fun MangaDto.toSManga(coverSize: Int): SManga {
        val attrs = attributes
        val cover = relationships.firstOrNull { it.type == "cover_art" }?.attributes?.fileName
        val tags = attrs.tags.mapNotNull { it.attributes.name["en"] }
        return SManga(
            url = id,
            title = preferredTitle(attrs),
            coverUrl = cover?.let { "$coversUrl/$id/$it.$coverSize.jpg" },
            author = relationships.filter { it.type == "author" }.mapNotNull { it.attributes?.name }.distinct()
                .joinToString().ifEmpty { null },
            artist = relationships.filter { it.type == "artist" }.mapNotNull { it.attributes?.name }.distinct()
                .joinToString().ifEmpty { null },
            description = (attrs.description[lang] ?: attrs.description["en"] ?: attrs.description.values.firstOrNull())
                ?.trim()?.ifEmpty { null },
            genres = tags,
            status = when (attrs.status) {
                "ongoing" -> SManga.Status.Ongoing
                "completed" -> SManga.Status.Completed
                "hiatus" -> SManga.Status.Hiatus
                "cancelled" -> SManga.Status.Cancelled
                else -> SManga.Status.Unknown
            },
            readingDirection = when {
                "Long Strip" in tags -> ReadingDirection.Webtoon
                attrs.originalLanguage == "ja" -> ReadingDirection.RightToLeft
                attrs.originalLanguage != null -> ReadingDirection.LeftToRight
                else -> null
            },
            originalLang = attrs.originalLanguage?.substringBefore('-'),
        )
    }

    /** A title in this source's language if MangaDex has one, then English, then whatever exists. */
    private fun preferredTitle(attrs: MangaAttributes): String =
        attrs.altTitles.firstNotNullOfOrNull { it[lang] }.takeIf { lang != "en" }
            ?: attrs.title["en"]
            ?: attrs.altTitles.firstNotNullOfOrNull { it["en"] }
            ?: attrs.title.values.firstOrNull()
            ?: "?"

    private fun ChapterDto.toSChapter(): SChapter {
        val attrs = attributes
        val name = buildString {
            attrs.volume?.let { append("Vol. ").append(it).append(' ') }
            attrs.chapter?.let { append("Ch. ").append(it) }
            attrs.title?.takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append(" - "); append(it) }
            if (isEmpty()) append("Oneshot")
        }.trim()
        return SChapter(
            url = id,
            name = name,
            number = attrs.chapter?.toFloatOrNull() ?: -1f,
            uploadedAt = attrs.publishAt?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() },
            scanlator = relationships.filter { it.type == "scanlation_group" }.mapNotNull { it.attributes?.name }
                .joinToString(" & ").ifEmpty { null },
        )
    }

    companion object {
        const val API_URL = "https://api.mangadex.org"
        const val COVERS_URL = "https://uploads.mangadex.org/covers"
        private const val PAGE_SIZE = 30
        private const val FEED_PAGE_SIZE = 500
        private const val MAX_OFFSET = 10_000
        private val ALL_CONTENT_RATINGS = listOf("safe", "suggestive", "erotica", "pornographic")

        /** Chapter languages offered as separate sources. */
        val LANGUAGES = listOf("en", "ru", "ja", "ko", "zh")

        internal val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    }
}

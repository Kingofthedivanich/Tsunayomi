package app.tsunayomi.source.mangadex

import app.tsunayomi.core.network.RateLimitInterceptor
import app.tsunayomi.core.network.await
import app.tsunayomi.core.network.bodyOrThrow
import app.tsunayomi.source.api.CatalogSource
import app.tsunayomi.source.api.ChaptersOnlyExternalException
import app.tsunayomi.source.api.Filter
import app.tsunayomi.source.api.FilterList
import app.tsunayomi.source.api.MangaPage
import app.tsunayomi.source.api.ReadingDirection
import app.tsunayomi.source.api.SChapter
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SPage
import app.tsunayomi.source.api.Source
import app.tsunayomi.source.api.sourceIdOf
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.OffsetDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * MangaDex through its public API (https://api.mangadex.org/docs).
 * One source for all languages: the chapter languages are a filter in the catalog, remembered as a setting
 * and also used for chapter lists. Terms: credit MangaDex and the scanlation groups, no ads or paid features.
 */
class MangaDexSource(
    client: OkHttpClient,
    /** Chapter languages chosen by the user (MangaDex codes, e.g. "en", "pt-br"). */
    private val languages: () -> Set<String>,
    /** Called when the user picks other languages in the filter, so chapter lists follow. */
    private val saveLanguages: suspend (Set<String>) -> Unit,
    private val apiUrl: HttpUrl = API_URL.toHttpUrl(),
    private val coversUrl: String = COVERS_URL,
) : CatalogSource {
    override val name: String = "MangaDex"
    override val lang: String = Source.MULTI_LANG
    override val id: Long = sourceIdOf(name, lang)
    override val supportsLatest: Boolean = true

    override fun webUrl(): String = "https://mangadex.org"
    override fun webUrl(manga: SManga): String = "https://mangadex.org/title/${manga.url}"

    /** The per-language sources this one replaced; their library and history entries resolve here. */
    override val legacyIds: Set<Long> = LEGACY_LANGUAGES.mapTo(HashSet()) { sourceIdOf(name, it) }

    // Stay under the documented ~5 req/s; the at-home endpoint has its own 40/min budget.
    private val apiClient = client.newBuilder().addInterceptor(RateLimitInterceptor(permits = 4, period = 1)).build()
    private val atHomeClient = apiClient.newBuilder()
        .addInterceptor(RateLimitInterceptor(permits = 40, period = 1, unit = TimeUnit.MINUTES))
        .build()

    override fun getFilters(): FilterList = MangaDexFilters.create(languages())

    override suspend fun getPopular(page: Int): MangaPage = searchManga(page, query = null, filters = getFilters())

    override suspend fun getLatest(page: Int): MangaPage {
        val filters = getFilters()
        (filters.first { it is Filter.Sort } as Filter.Sort).state =
            Filter.Sort.Selection(MangaDexFilters.sortOptions.indexOfFirst { it.second == "latestUploadedChapter" }, ascending = false)
        return searchManga(page, query = null, filters = filters)
    }

    override suspend fun search(query: String, filters: FilterList, page: Int): MangaPage {
        val chosen = MangaDexFilters.selectedLanguages(filters)
        if (chosen.isNotEmpty() && chosen != languages()) saveLanguages(chosen)
        return searchManga(page, query.trim().ifEmpty { null }, filters.ifEmpty { getFilters() })
    }

    // Tag names (as shown on titles, in English) to MangaDex tag ids; the list rarely changes, so it's kept once loaded.
    @Volatile
    private var tagIds: Map<String, String>? = null

    private var tagNames: List<String> = emptyList()

    private suspend fun loadTagIds(): Map<String, String> = tagIds ?: run {
        val tags = json.decodeFromString<TagListResponse>(get(apiUrl.newBuilder().addPathSegments("manga/tag").build())).data
            .mapNotNull { dto -> dto.attributes.name["en"]?.let { it to dto.id } }
        tagNames = tags.map { it.first }.sortedBy { it.lowercase() }
        tags.associate { (name, id) -> name.lowercase() to id }.also { tagIds = it }
    }

    override suspend fun searchableTags(): List<String> {
        loadTagIds()
        return tagNames
    }

    override suspend fun searchByTags(tags: Set<String>, page: Int): MangaPage? {
        val ids = loadTagIds()
        val wanted = tags.map { ids[it.lowercase()] ?: return null }
        return searchManga(page, query = null, filters = getFilters(), tagIds = wanted)
    }

    // MangaDex matches its text search against titles only, so the author is looked up and titles are listed by id.
    override suspend fun searchByAuthor(author: String, page: Int): MangaPage {
        val url = apiUrl.newBuilder().addPathSegment("author")
            .addQueryParameter("name", author)
            .addQueryParameter("limit", "10")
            .build()
        val found = json.decodeFromString<AuthorListResponse>(get(url)).data
        val id = (found.firstOrNull { it.attributes?.name.equals(author, ignoreCase = true) } ?: found.firstOrNull())?.id
            ?: return MangaPage(emptyList(), hasNext = false)
        return searchManga(page, query = null, filters = getFilters(), authorId = id)
    }

    private suspend fun searchManga(
        page: Int,
        query: String?,
        filters: List<Filter<*>>,
        tagIds: List<String> = emptyList(),
        authorId: String? = null,
    ): MangaPage {
        val offset = (page - 1) * PAGE_SIZE
        val chapterLanguages = MangaDexFilters.selectedLanguages(filters).ifEmpty { languages() }
        val url = apiUrl.newBuilder().addPathSegment("manga").apply {
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("offset", offset.toString())
            addQueryParameter("includes[]", "cover_art")
            chapterLanguages.forEach { addQueryParameter("availableTranslatedLanguage[]", it) }
            addQueryParameter("hasAvailableChapters", "true")
            if (query != null) addQueryParameter("title", query)
            // MangaDex requires every included tag by default (includedTagsMode=AND).
            tagIds.forEach { addQueryParameter("includedTags[]", it) }
            if (authorId != null) addQueryParameter("authorOrArtist", authorId)
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
                .apply { languages().forEach { addQueryParameter("translatedLanguage[]", it) } }
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

        val readable = chapters.filter { it.attributes.externalUrl == null && it.attributes.pages > 0 }
        if (readable.isEmpty()) {
            val external = externalCount(manga.url)
            if (external > 0) throw ChaptersOnlyExternalException(external)
        }
        return readable
            .map { it.toSChapter() }
            // Volume-less chapters come last from the API; order everything by number, keeping group variants together.
            .sortedBy { chapter -> chapter.number.takeIf { it >= 0 } ?: Float.MAX_VALUE }
    }

    /**
     * The feed request excludes external chapters, so ask once more only when nothing readable came back:
     * a total above zero means the series is licensed and lives on the publisher's site.
     */
    private suspend fun externalCount(mangaId: String): Int {
        val url = apiUrl.newBuilder().addPathSegment("manga").addPathSegment(mangaId).addPathSegment("feed")
            .apply { languages().forEach { addQueryParameter("translatedLanguage[]", it) } }
            .addQueryParameter("limit", "1")
            .apply { ALL_CONTENT_RATINGS.forEach { addQueryParameter("contentRating[]", it) } }
            .build()
        return json.decodeFromString<ChapterListResponse>(get(url)).total
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
            description = (attrs.description[displayLanguage()] ?: attrs.description["en"] ?: attrs.description.values.firstOrNull())
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

    /** The app language when it is among the chosen chapter languages, otherwise English. */
    private fun displayLanguage(): String {
        val app = Locale.getDefault().language
        return if (languages().any { it.substringBefore('-') == app }) app else "en"
    }

    /** A title in the display language if MangaDex has one, then English, then whatever exists. */
    private fun preferredTitle(attrs: MangaAttributes): String =
        displayLanguage().let { display -> attrs.altTitles.firstNotNullOfOrNull { it[display] }.takeIf { display != "en" } }
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
            language = attrs.translatedLanguage,
        )
    }

    companion object {
        const val API_URL = "https://api.mangadex.org"
        const val COVERS_URL = "https://uploads.mangadex.org/covers"
        private const val PAGE_SIZE = 30
        private const val FEED_PAGE_SIZE = 500
        private const val MAX_OFFSET = 10_000
        private val ALL_CONTENT_RATINGS = listOf("safe", "suggestive", "erotica", "pornographic")

        /** Languages that used to be separate sources, for [legacyIds]. */
        private val LEGACY_LANGUAGES = listOf("en", "ru", "ja", "ko", "zh")

        /** Chapter languages offered in the filter (MangaDex codes). */
        val LANGUAGES = listOf(
            "en", "ru", "uk", "ja", "ko", "zh", "zh-hk", "es", "es-la", "pt-br", "fr", "de", "it", "pl", "tr", "id", "vi", "th",
        )

        /** Until the user picks: the app language if MangaDex has it, plus English. */
        fun defaultLanguages(appLanguage: String = Locale.getDefault().language): Set<String> =
            linkedSetOf<String>().apply {
                if (appLanguage in LANGUAGES) add(appLanguage)
                add("en")
            }

        internal val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    }
}

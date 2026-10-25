package app.tsunayomi.source.remanga

import app.tsunayomi.core.network.RateLimitInterceptor
import app.tsunayomi.core.network.await
import app.tsunayomi.core.network.bodyOrThrow
import app.tsunayomi.source.api.CatalogSource
import app.tsunayomi.source.api.Filter
import app.tsunayomi.source.api.FilterList
import app.tsunayomi.source.api.MangaPage
import app.tsunayomi.source.api.SChapter
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SPage
import app.tsunayomi.source.api.sourceIdOf
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Built-in source for ReManga (remanga.org), a large Russian manga and manhwa catalog.
 */
class RemangaSource(
    client: OkHttpClient,
    private val apiBaseUrl: HttpUrl = API_BASE_URL.toHttpUrl(),
) : CatalogSource {

    override val name: String = "Remanga"
    override val lang: String = "ru"
    override val id: Long = sourceIdOf(name, lang)
    override val supportsLatest: Boolean = true

    override fun webUrl(): String = "https://remanga.org"
    override fun webUrl(manga: SManga): String = "https://remanga.org/manga/${manga.url}"

    private val apiClient = client.newBuilder()
        .addInterceptor(RateLimitInterceptor(permits = 4, period = 1))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override fun getFilters(): FilterList = RemangaFilters.create()

    override suspend fun getPopular(page: Int): MangaPage {
        val url = apiBaseUrl.newBuilder()
            .addPathSegment("search")
            .addPathSegment("catalog")
            .addQueryParameter("ordering", "-rating")
            .addQueryParameter("count", PAGE_SIZE.toString())
            .addQueryParameter("page", page.toString())
            .build()
        return fetchCatalogPage(url, page)
    }

    override suspend fun getLatest(page: Int): MangaPage {
        val url = apiBaseUrl.newBuilder()
            .addPathSegment("search")
            .addPathSegment("catalog")
            .addQueryParameter("ordering", "-chapter_date")
            .addQueryParameter("count", PAGE_SIZE.toString())
            .addQueryParameter("page", page.toString())
            .build()
        return fetchCatalogPage(url, page)
    }

    override suspend fun search(query: String, filters: FilterList, page: Int): MangaPage {
        val urlBuilder = apiBaseUrl.newBuilder()
            .addPathSegment("search")
            .addPathSegment("catalog")
            .addQueryParameter("count", PAGE_SIZE.toString())
            .addQueryParameter("page", page.toString())

        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotEmpty()) {
            urlBuilder.addQueryParameter("query", trimmedQuery)
        }

        var orderingApplied = false
        filters.forEach { filter ->
            when (filter) {
                is Filter.Select -> {
                    when (filter.name) {
                        "Сортировка" -> {
                            val ordering = RemangaFilters.sortOptions.getOrNull(filter.state)?.second
                            if (!ordering.isNullOrEmpty()) {
                                urlBuilder.addQueryParameter("ordering", ordering)
                                orderingApplied = true
                            }
                        }
                        "Тип произведения" -> {
                            val type = RemangaFilters.typeOptions.getOrNull(filter.state)?.second
                            if (!type.isNullOrEmpty()) {
                                urlBuilder.addQueryParameter("type", type)
                            }
                        }
                        "Возрастной рейтинг" -> {
                            val age = RemangaFilters.ageRatingOptions.getOrNull(filter.state)?.second
                            if (!age.isNullOrEmpty()) {
                                urlBuilder.addQueryParameter("age_limit", age)
                            }
                        }
                    }
                }
                else -> Unit
            }
        }

        if (!orderingApplied) {
            urlBuilder.addQueryParameter("ordering", "-rating")
        }

        return fetchCatalogPage(urlBuilder.build(), page)
    }

    // Remanga lists a creator's titles by id only; ids of the writers and artists seen on title pages are kept here,
    // so tapping a name on such a page finds the right person. Names from other sources aren't known.
    private val creatorIds = ConcurrentHashMap<String, Long>()

    // Genre names to ids; the list rarely changes.
    @Volatile
    private var genreIds: Map<String, Int>? = null

    override suspend fun searchByAuthor(author: String, page: Int): MangaPage? {
        val id = creatorIds[author.lowercase()] ?: return null
        // Only the v2 catalog filters by creator; its "-rating" puts barely rated titles first, "-avg_rating" doesn't.
        val url = apiBaseUrl.newBuilder()
            .addPathSegments("v2/search/catalog")
            .addQueryParameter("creators", id.toString())
            .addQueryParameter("ordering", "-avg_rating")
            .addQueryParameter("page", page.toString())
            .build()
        val response = json.decodeFromString<RemangaV2CatalogResponse>(get(url))
        return MangaPage(items = response.results.map { it.toSManga() }, hasNext = response.next != null)
    }

    private var genreNames: List<String> = emptyList()

    private suspend fun loadGenreIds(): Map<String, Int> = genreIds ?: run {
        val url = apiBaseUrl.newBuilder().addPathSegments("forms/titles").addQueryParameter("get", "genres").build()
        val genres = json.decodeFromString<RemangaFormsResponse>(get(url)).content?.genres.orEmpty()
        genreNames = genres.map { it.name }.sortedBy { it.lowercase() }
        genres.associate { it.name.lowercase() to it.id }.also { genreIds = it }
    }

    override suspend fun searchableTags(): List<String> {
        loadGenreIds()
        return genreNames
    }

    override suspend fun searchByTags(tags: Set<String>, page: Int): MangaPage? {
        val ids = loadGenreIds()
        val wanted = tags.map { tag -> tag to (ids[tag.lowercase()] ?: return null) }
        // Several genres in one request match any of them, so the catalog is asked for the first one and the rest are
        // checked against each title's genres.
        val url = apiBaseUrl.newBuilder()
            .addPathSegment("search")
            .addPathSegment("catalog")
            .addQueryParameter("genres", wanted.first().second.toString())
            .addQueryParameter("ordering", "-rating")
            .addQueryParameter("count", PAGE_SIZE.toString())
            .addQueryParameter("page", page.toString())
            .build()
        val result = fetchCatalogPage(url, page)
        val others = wanted.drop(1).map { it.first.lowercase() }
        if (others.isEmpty()) return result
        return result.copy(items = result.items.filter { manga -> others.all { tag -> manga.genres.any { it.lowercase() == tag } } })
    }

    private suspend fun fetchCatalogPage(url: HttpUrl, page: Int): MangaPage {
        val body = get(url)
        val response = json.decodeFromString<RemangaCatalogResponse>(body)
        val items = response.content.map { it.toSManga() }
        val totalPages = response.props?.totalPages ?: 0
        val hasNext = if (totalPages > 0) page < totalPages else items.size >= PAGE_SIZE
        return MangaPage(items = items, hasNext = hasNext)
    }

    override suspend fun getDetails(manga: SManga): SManga {
        val slug = manga.url.removePrefix("/manga/").trim('/')
        val url = apiBaseUrl.newBuilder()
            .addPathSegment("titles")
            .addPathSegment(slug)
            .build()
        val body = get(url)
        val response = json.decodeFromString<RemangaTitleResponse>(body)
        val details = response.content ?: return manga

        val preferredTitle = details.rusName?.takeIf { it.isNotBlank() }
            ?: details.mainName
            ?: details.enName
            ?: manga.title

        val coverUrl = (details.img?.bestUrl() ?: details.cover?.bestUrl())?.let { toAbsoluteMediaUrl(it) }
            ?: manga.coverUrl

        val description = details.description?.replace(HTML_TAG_REGEX, "")?.trim()?.ifBlank { null }

        val status = when (details.status?.id) {
            1 -> SManga.Status.Completed
            2 -> SManga.Status.Ongoing
            3, 4 -> SManga.Status.Cancelled
            else -> SManga.Status.Unknown
        }

        val writers = details.creators["1"].orEmpty()
        val artists = details.creators["2"].orEmpty()
        (writers + artists).forEach { if (it.name.isNotBlank()) creatorIds[it.name.lowercase()] = it.id }

        return manga.copy(
            title = preferredTitle,
            coverUrl = coverUrl,
            description = description,
            author = writers.joinToString { it.name }.ifBlank { null } ?: manga.author,
            artist = artists.joinToString { it.name }.ifBlank { null } ?: manga.artist,
            genres = details.genres.map { it.name },
            status = status,
            originalLang = PAGE_LANGUAGE,
        )
    }

    override suspend fun getChapters(manga: SManga): List<SChapter> {
        val slug = manga.url.removePrefix("/manga/").trim('/')
        val titleUrl = apiBaseUrl.newBuilder()
            .addPathSegment("titles")
            .addPathSegment(slug)
            .build()
        val titleBody = get(titleUrl)
        val titleResponse = json.decodeFromString<RemangaTitleResponse>(titleBody)
        val branches = titleResponse.content?.branches.orEmpty()
        if (branches.isEmpty()) return emptyList()

        val chaptersList = mutableListOf<SChapter>()

        for (branch in branches) {
            var page = 1
            while (true) {
                val url = apiBaseUrl.newBuilder()
                    .addPathSegment("titles")
                    .addPathSegment("chapters")
                    .addQueryParameter("branch_id", branch.id.toString())
                    .addQueryParameter("count", "100")
                    .addQueryParameter("page", page.toString())
                    .build()
                val body = get(url)
                val response = json.decodeFromString<RemangaChaptersResponse>(body)
                val items = response.content
                if (items.isEmpty()) break

                for (item in items) {
                    if (item.isPaid && !item.isBought) continue

                    val nameSuffix = item.name?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""
                    val title = "Том ${item.tome} Гл. ${item.chapter}$nameSuffix"
                    val uploadDateMs = parseDate(item.uploadDate)
                    val scanlator = item.publishers.firstOrNull()?.name
                        ?: branch.publishers.firstOrNull()?.name

                    chaptersList.add(
                        SChapter(
                            url = item.id.toString(),
                            name = title,
                            number = item.chapter.toFloatOrNull() ?: 0f,
                            uploadedAt = uploadDateMs,
                            scanlator = scanlator,
                        ),
                    )
                }

                if (items.size < 100) break
                page++
            }
        }

        // Reading order: the first chapter first; a chapter several teams translated keeps its uploads together.
        return chaptersList.sortedWith(
            compareBy<SChapter> { it.number }.thenBy { it.uploadedAt ?: 0L },
        )
    }

    override suspend fun getPages(chapter: SChapter): List<SPage> {
        val url = apiBaseUrl.newBuilder()
            .addPathSegment("titles")
            .addPathSegment("chapters")
            .addPathSegment(chapter.url)
            .build()
        val body = get(url)
        val response = json.decodeFromString<RemangaChapterDetailsResponse>(body)
        val links = extractPageLinks(response.content?.pages ?: response.pages)

        return links.mapIndexed { index, link ->
            val pageUrl = toAbsoluteMediaUrl(link)
            SPage.Remote(index = index, imageUrl = pageUrl)
        }
    }

    private suspend fun get(url: HttpUrl): String =
        apiClient.newCall(Request.Builder().url(url).build()).await().bodyOrThrow()

    private fun RemangaCatalogItemDto.toSManga(): SManga {
        val preferredTitle = rusName?.takeIf { it.isNotBlank() }
            ?: mainName
            ?: enName
            ?: dir
        val coverUrl = (img?.bestUrl() ?: cover?.bestUrl())?.let { toAbsoluteMediaUrl(it) }
        return SManga(
            url = dir,
            title = preferredTitle,
            coverUrl = coverUrl,
            status = SManga.Status.Unknown,
            genres = genres.map { it.name },
            originalLang = PAGE_LANGUAGE,
        )
    }

    private fun toAbsoluteMediaUrl(url: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("/") -> "https://remanga.org$url"
            else -> "https://remanga.org/$url"
        }
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return runCatching {
            OffsetDateTime.parse(dateStr).toInstant().toEpochMilli()
        }.getOrElse {
            runCatching {
                LocalDateTime.parse(dateStr).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrDefault(0L)
        }
    }

    companion object {
        /** Remanga hosts Russian translations: the text on the pages is Russian whatever the original was. */
        const val PAGE_LANGUAGE = "ru"

        const val API_BASE_URL = "https://api.remanga.org/api/"
        private const val PAGE_SIZE = 30
        private val HTML_TAG_REGEX = Regex("<[^>]*>")
    }
}

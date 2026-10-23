package app.tsunayomi.source.komga

import app.tsunayomi.core.common.ApplicationScope
import app.tsunayomi.core.datastore.ServerPreferencesDataSource
import app.tsunayomi.core.model.KomgaConfig
import app.tsunayomi.core.network.HostAuthRegistry
import app.tsunayomi.core.network.await
import app.tsunayomi.core.network.bodyOrThrow
import app.tsunayomi.source.api.CatalogSource
import app.tsunayomi.source.api.FilterList
import app.tsunayomi.source.api.MangaPage
import app.tsunayomi.source.api.ReadingDirection
import app.tsunayomi.source.api.SChapter
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SPage
import app.tsunayomi.source.api.Source
import app.tsunayomi.source.api.SourceIds
import app.tsunayomi.source.api.SourceNotConfiguredException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A self-hosted Komga server (https://komga.org) through its REST API.
 * Credentials are handed to [HostAuthRegistry], so Coil's cover and page requests are authorized too.
 */
class KomgaSource(
    private val client: OkHttpClient,
    private val configs: Flow<KomgaConfig?>,
    private val hostAuth: HostAuthRegistry,
) : CatalogSource {
    override val id: Long = SourceIds.KOMGA
    override val name: String = "Komga"
    override val lang: String = Source.MULTI_LANG
    override val supportsLatest: Boolean = true

    /** Keeps the auth registry in sync with saved settings, including on app start before any source call. */
    fun syncAuth(scope: CoroutineScope) {
        configs.runningFold(null as HttpUrl? to null as KomgaConfig?) { (previousUrl, _), config ->
            previousUrl?.let(hostAuth::clear)
            val url = config?.url?.toHttpUrlOrNull()
            if (url != null && config.hasCredentials) hostAuth.set(url, authHeaders(config))
            url to config
        }.launchIn(scope)
    }

    override suspend fun getPopular(page: Int): MangaPage = listSeries(page, query = null, latest = false)

    override suspend fun getLatest(page: Int): MangaPage = listSeries(page, query = null, latest = true)

    override suspend fun search(query: String, filters: FilterList, page: Int): MangaPage =
        listSeries(page, query.trim().ifEmpty { null }, latest = false)

    private suspend fun listSeries(page: Int, query: String?, latest: Boolean): MangaPage {
        val base = baseUrl()
        val url = base.newBuilder().addPathSegments(if (latest) "api/v1/series/latest" else "api/v1/series")
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("size", PAGE_SIZE.toString())
            .apply {
                if (!latest) addQueryParameter("sort", "metadata.titleSort,asc")
                if (query != null) addQueryParameter("search", query)
            }
            .build()
        val response = json.decodeFromString<PageDto<SeriesDto>>(get(url))
        return MangaPage(response.content.map { it.toSManga(base) }, hasNext = !response.last)
    }

    override suspend fun getDetails(manga: SManga): SManga {
        val base = baseUrl()
        val url = base.newBuilder().addPathSegments("api/v1/series").addPathSegment(manga.url).build()
        return json.decodeFromString<SeriesDto>(get(url)).toSManga(base)
    }

    override suspend fun getChapters(manga: SManga): List<SChapter> {
        val url = baseUrl().newBuilder().addPathSegments("api/v1/series").addPathSegment(manga.url).addPathSegment("books")
            .addQueryParameter("unpaged", "true")
            .addQueryParameter("sort", "metadata.numberSort,asc")
            .build()
        return json.decodeFromString<PageDto<BookDto>>(get(url)).content
            .filter { it.media.pagesCount > 0 }
            .map { book ->
                SChapter(
                    url = book.id,
                    name = book.metadata.title.ifBlank { book.name },
                    number = book.metadata.numberSort.takeIf { it >= 0 } ?: book.number,
                    uploadedAt = book.metadata.releaseDate?.parseDate() ?: book.fileLastModified?.parseInstant(),
                )
            }
    }

    override suspend fun getPages(chapter: SChapter): List<SPage> {
        val base = baseUrl()
        val url = base.newBuilder().addPathSegments("api/v1/books").addPathSegment(chapter.url).addPathSegment("pages").build()
        return json.decodeFromString<List<BookPageDto>>(get(url)).mapIndexed { index, page ->
            val image = base.newBuilder().addPathSegments("api/v1/books").addPathSegment(chapter.url)
                .addPathSegment("pages").addPathSegment(page.number.toString())
                .build()
            // Without an image Accept header Komga serves PDF pages as single-page PDFs, which Coil cannot decode.
            SPage.Remote(index, image.toString(), headers = mapOf("Accept" to "image/*"))
        }
    }

    private suspend fun baseUrl(): HttpUrl {
        val config = configs.first()
            ?: throw SourceNotConfiguredException("Komga server is not set up")
        val url = config.url.toHttpUrlOrNull()
            ?: throw SourceNotConfiguredException("Invalid Komga address: ${config.url}")
        // Make sure auth is registered even if syncAuth has not run yet (e.g. in tests).
        if (config.hasCredentials) hostAuth.set(url, authHeaders(config))
        return url
    }

    private suspend fun get(url: HttpUrl): String =
        client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).await().bodyOrThrow()

    private fun SeriesDto.toSManga(base: HttpUrl): SManga {
        val authors = booksMetadata.authors
        return SManga(
            url = id,
            title = metadata.title?.ifBlank { null } ?: name,
            coverUrl = base.newBuilder().addPathSegments("api/v1/series").addPathSegment(id).addPathSegment("thumbnail")
                .build().toString(),
            author = authors.filter { it.role == "writer" }.joinToString { it.name }.ifEmpty { null },
            artist = authors.filter { it.role == "penciller" }.joinToString { it.name }.ifEmpty { null },
            description = metadata.summary.ifBlank { booksMetadata.summary }.ifBlank { null },
            genres = (metadata.genres + metadata.tags).toList(),
            status = when (metadata.status) {
                "ONGOING" -> SManga.Status.Ongoing
                "ENDED" -> SManga.Status.Completed
                "HIATUS" -> SManga.Status.Hiatus
                "ABANDONED" -> SManga.Status.Cancelled
                else -> SManga.Status.Unknown
            },
            readingDirection = when (metadata.readingDirection) {
                "LEFT_TO_RIGHT" -> ReadingDirection.LeftToRight
                "RIGHT_TO_LEFT" -> ReadingDirection.RightToLeft
                "VERTICAL" -> ReadingDirection.Vertical
                "WEBTOON" -> ReadingDirection.Webtoon
                else -> null
            },
            originalLang = metadata.language.substringBefore('-').lowercase().ifBlank { null },
        )
    }

    private fun String.parseDate(): Long? =
        runCatching { LocalDate.parse(this).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()

    private fun String.parseInstant(): Long? = runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()

    companion object {
        private const val PAGE_SIZE = 30
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        internal fun authHeaders(config: KomgaConfig): Map<String, String> =
            if (config.apiKey.isNotBlank()) {
                mapOf("X-API-Key" to config.apiKey)
            } else {
                mapOf("Authorization" to Credentials.basic(config.username, config.password))
            }
    }
}

@Singleton
internal class KomgaSourceHolder @Inject constructor(
    client: OkHttpClient,
    preferences: ServerPreferencesDataSource,
    hostAuth: HostAuthRegistry,
    @ApplicationScope scope: CoroutineScope,
) {
    val source = KomgaSource(client, preferences.komga, hostAuth).also { it.syncAuth(scope) }
}

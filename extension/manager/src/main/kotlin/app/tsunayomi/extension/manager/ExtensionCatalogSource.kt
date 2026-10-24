package app.tsunayomi.extension.manager

import app.tsunayomi.source.api.CatalogSource
import app.tsunayomi.source.api.Filter
import app.tsunayomi.source.api.FilterList
import app.tsunayomi.source.api.MangaPage
import app.tsunayomi.source.api.SChapter
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SPage
import app.tsunayomi.source.api.Source
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import java.util.Collections
import java.util.WeakHashMap
import eu.kanade.tachiyomi.source.model.Filter as EFilter
import eu.kanade.tachiyomi.source.model.FilterList as EFilterList
import eu.kanade.tachiyomi.source.model.SChapter as EChapter
import eu.kanade.tachiyomi.source.model.SManga as EManga

/** Presents a source from a Tachiyomi/Mihon extension as one of the app's own catalog sources. */
internal class ExtensionCatalogSource(
    val delegate: CatalogueSource,
    override val iconUri: String?,
) : CatalogSource {
    override val id: Long = delegate.id
    override val name: String = delegate.name
    override val lang: String = delegate.lang.toAppLang()
    override val supportsLatest: Boolean get() = delegate.supportsLatest
    override val hasSettings: Boolean get() = delegate is ConfigurableSource

    override fun webUrl(): String? = (delegate as? HttpSource)?.getHomeUrl() ?: (delegate as? HttpSource)?.baseUrl

    override fun webUrl(manga: SManga): String? = runCatching {
        (delegate as? HttpSource)?.getMangaUrl(manga.toExtension())
    }.getOrNull()

    // The filter objects shown to the user, mapped back to the extension's own ones (filters are compared by identity).
    private val filterLinks = Collections.synchronizedMap(WeakHashMap<Filter<*>, EFilter<*>>())
    private val listLinks = Collections.synchronizedMap(WeakHashMap<FilterList, EFilterList>())

    override fun getFilters(): FilterList {
        val extensionFilters = runCatching { delegate.getFilterList() }.getOrElse { return FilterList() }
        val filters = FilterList(extensionFilters.mapNotNull(::toAppFilter))
        listLinks[filters] = extensionFilters
        return filters
    }

    override suspend fun getPopular(page: Int): MangaPage = guard { delegate.getPopularManga(page).toPage() }

    override suspend fun getLatest(page: Int): MangaPage = guard { delegate.getLatestUpdates(page).toPage() }

    override suspend fun search(query: String, filters: FilterList, page: Int): MangaPage = guard {
        val extensionFilters = listLinks[filters]?.also { list -> filters.forEach(::writeBack) }
            ?: delegate.getFilterList()
        delegate.getSearchManga(page, query, extensionFilters).toPage()
    }

    override suspend fun getDetails(manga: SManga): SManga = guard {
        val update = delegate.getMangaUpdate(manga.toExtension(), emptyList(), fetchDetails = true, fetchChapters = false)
        update.manga.toApp(fallback = manga)
    }

    override suspend fun getChapters(manga: SManga): List<SChapter> = guard {
        val extensionManga = manga.toExtension()
        val update = delegate.getMangaUpdate(extensionManga, emptyList(), fetchDetails = false, fetchChapters = true)
        val httpSource = delegate as? HttpSource
        // Extensions list the newest chapter first.
        update.chapters.asReversed().map { chapter ->
            httpSource?.prepareNewChapter(chapter, extensionManga)
            SChapter(
                url = chapter.url,
                name = chapter.name,
                number = chapter.chapter_number,
                uploadedAt = chapter.date_upload.takeIf { it > 0 },
                scanlator = chapter.scanlator?.takeIf { it.isNotBlank() },
            )
        }
    }

    override suspend fun getPages(chapter: SChapter): List<SPage> = guard {
        val pages = delegate.getPageList(
            EChapter.create().apply {
                url = chapter.url
                name = chapter.name
                chapter_number = chapter.number
                scanlator = chapter.scanlator
                date_upload = chapter.uploadedAt ?: 0
            },
        )
        // Images are fetched through the extension's own client (see ExtensionImageInterceptor), which adds
        // the headers the site expects and runs any interceptor that unscrambles pages.
        pages.mapIndexed { index, page ->
            val imageUrl = page.imageUrl
            SPage.Remote(
                index = index,
                imageUrl = if (imageUrl != null) {
                    ExtensionUrls.image(id, imageUrl)
                } else {
                    ExtensionUrls.page(id, page.url, page.index)
                },
            )
        }
    }

    private fun EFilterList.forEach(block: (EFilter<*>) -> Unit) = (this as List<EFilter<*>>).forEach(block)

    private fun toAppFilter(filter: EFilter<*>): Filter<*>? {
        val converted: Filter<*> = when (filter) {
            is EFilter.Header -> Filter.Header(filter.name)
            is EFilter.Separator -> return null
            is EFilter.Text -> Filter.Text(filter.name, filter.state)
            is EFilter.CheckBox -> Filter.CheckBox(filter.name, filter.state)
            is EFilter.TriState -> Filter.TriState(
                filter.name,
                when (filter.state) {
                    EFilter.TriState.STATE_INCLUDE -> Filter.TriState.Value.Include
                    EFilter.TriState.STATE_EXCLUDE -> Filter.TriState.Value.Exclude
                    else -> Filter.TriState.Value.Ignore
                },
            )
            is EFilter.Select<*> -> Filter.Select(filter.name, filter.values.map { it.toString() }, filter.state)
            is EFilter.Sort -> Filter.Sort(
                filter.name,
                filter.values.toList(),
                filter.state?.let { Filter.Sort.Selection(it.index, it.ascending) },
            )
            is EFilter.Group<*> -> Filter.Group(
                filter.name,
                filter.state.mapNotNull { (it as? EFilter<*>)?.let(::toAppFilter) },
            )
        }
        filterLinks[converted] = filter
        return converted
    }

    @Suppress("UNCHECKED_CAST")
    private fun writeBack(filter: Filter<*>) {
        val target = filterLinks[filter] ?: return
        when (filter) {
            is Filter.Text -> (target as EFilter<String>).state = filter.state
            is Filter.CheckBox -> (target as EFilter<Boolean>).state = filter.state
            is Filter.TriState -> (target as EFilter<Int>).state = when (filter.state) {
                Filter.TriState.Value.Include -> EFilter.TriState.STATE_INCLUDE
                Filter.TriState.Value.Exclude -> EFilter.TriState.STATE_EXCLUDE
                Filter.TriState.Value.Ignore -> EFilter.TriState.STATE_IGNORE
            }
            is Filter.Select -> (target as EFilter<Int>).state = filter.state
            is Filter.Sort -> (target as EFilter<EFilter.Sort.Selection?>).state =
                filter.state?.let { EFilter.Sort.Selection(it.index, it.ascending) }
            is Filter.Group<*> -> filter.state.forEach(::writeBack)
            is Filter.Header -> Unit
        }
    }

    private fun eu.kanade.tachiyomi.source.model.MangasPage.toPage() =
        MangaPage(mangas.map { it.toApp(fallback = null) }, hasNextPage)

    private fun EManga.toApp(fallback: SManga?) = SManga(
        url = url,
        title = runCatching { title }.getOrNull() ?: fallback?.title.orEmpty(),
        coverUrl = thumbnail_url?.takeIf { it.isNotBlank() }?.let { ExtensionUrls.cover(id, it) } ?: fallback?.coverUrl,
        author = author ?: fallback?.author,
        artist = artist ?: fallback?.artist,
        description = description ?: fallback?.description,
        genres = genre?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: fallback?.genres.orEmpty(),
        status = when (status) {
            EManga.ONGOING -> SManga.Status.Ongoing
            EManga.COMPLETED, EManga.PUBLISHING_FINISHED -> SManga.Status.Completed
            EManga.ON_HIATUS -> SManga.Status.Hiatus
            EManga.CANCELLED -> SManga.Status.Cancelled
            else -> fallback?.status ?: SManga.Status.Unknown
        },
        originalLang = fallback?.originalLang ?: lang.takeIf { it != Source.MULTI_LANG }?.substringBefore('-'),
    )

    private fun SManga.toExtension(): EManga = EManga.create().also {
        it.url = url
        it.title = title
        it.thumbnail_url = coverUrl?.let(ExtensionUrls::original)
        it.author = author
        it.artist = artist
        it.description = description
        it.genre = genres.joinToString(", ").ifEmpty { null }
        it.initialized = true
    }

    /** The image request for a page, used by [ExtensionImageInterceptor]. */
    suspend fun fetchImage(pageUrl: String, pageIndex: Int, imageUrl: String?): okhttp3.Response {
        val source = delegate as? HttpSource ?: error("$name can't load images")
        val page = Page(pageIndex, pageUrl, imageUrl)
        if (page.imageUrl == null) page.imageUrl = source.getImageUrl(page)
        return source.getImage(page)
    }

    suspend fun fetchCover(url: String): okhttp3.Response {
        val source = delegate as? HttpSource
        val request = eu.kanade.tachiyomi.network.GET(url, source?.headers ?: okhttp3.Headers.headersOf())
        return (source?.client ?: error("$name can't load images")).newCall(request).awaitSuccess()
    }
}

/**
 * Extension code can fail with any Throwable, e.g. NoClassDefFoundError when it needs a library the app doesn't
 * have. The app handles Exceptions, so everything else is turned into one instead of crashing it.
 */
internal suspend fun <T> guard(block: suspend () -> T): T = try {
    block()
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    throw e
} catch (e: Throwable) {
    throw ExtensionException(e)
}

class ExtensionException(cause: Throwable) : Exception(cause.toString(), cause)

/** Tachiyomi uses "all" and "other" for multi-language sources and regional codes like "pt-BR". */
internal fun String.toAppLang(): String = when (this) {
    "all", "other", "" -> Source.MULTI_LANG
    else -> this
}

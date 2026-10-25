package app.tsunayomi.source.kotatsu

import app.tsunayomi.source.api.CatalogSource
import app.tsunayomi.source.api.FilterList
import app.tsunayomi.source.api.MangaPage
import app.tsunayomi.source.api.SChapter
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SPage
import app.tsunayomi.source.api.Source
import app.tsunayomi.source.api.sourceIdOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder

internal class KotatsuCatalogSource(
    val parserSource: MangaParserSource,
    val parser: MangaParser,
    val isNsfw: Boolean = false,
) : CatalogSource {

    override val id: Long = sourceIdOf("kotatsu_${parserSource.name}", parserSource.locale.ifBlank { Source.MULTI_LANG })

    override val name: String = parserSource.title

    override val lang: String = parserSource.locale.takeIf { it.isNotBlank() } ?: Source.MULTI_LANG

    override val supportsLatest: Boolean = parser.availableSortOrders.contains(SortOrder.UPDATED)

    override val hasSettings: Boolean = false

    override fun webUrl(): String? = "https://${parser.domain}"

    override fun webUrl(manga: SManga): String? {
        val url = manga.url
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://${parser.domain}$url"
        }
    }

    override fun webHeaders(): Map<String, String> = parser.getRequestHeaders().toMap()

    override fun getFilters(): FilterList = FilterList()

    // The parser's tag list usually takes a request, and its tags carry the site's own keys; kept once loaded.
    @Volatile
    private var availableTags: Set<MangaTag>? = null

    private suspend fun loadTags(): Set<MangaTag> =
        availableTags ?: parser.getFilterOptions().availableTags.also { availableTags = it }

    override suspend fun searchableTags(): List<String>? = withContext(Dispatchers.IO) {
        loadTags().map { it.title }.sortedBy { it.lowercase() }.ifEmpty { null }
    }

    override suspend fun searchByTags(tags: Set<String>, page: Int): MangaPage? = withContext(Dispatchers.IO) {
        if (tags.size > 1 && !parser.filterCapabilities.isMultipleTagsSupported) return@withContext null
        val available = loadTags()
        val matches = tags.map { tag -> available.firstOrNull { it.title.equals(tag, ignoreCase = true) } ?: return@withContext null }
        val sortOrder = parser.availableSortOrders.firstOrNull() ?: SortOrder.POPULARITY
        val list = parser.getList(offset = maxOf(0, page - 1) * 20, order = sortOrder, filter = MangaListFilter(tags = matches.toSet()))
        MangaPage(items = list.map { it.toSManga() }, hasNext = list.isNotEmpty())
    }

    override suspend fun searchByAuthor(author: String, page: Int): MangaPage? =
        if (parser.filterCapabilities.isSearchSupported) search(author, getFilters(), page) else null

    override suspend fun getPopular(page: Int): MangaPage = withContext(Dispatchers.IO) {
        val sortOrder = if (parser.availableSortOrders.contains(SortOrder.POPULARITY)) {
            SortOrder.POPULARITY
        } else {
            parser.availableSortOrders.firstOrNull() ?: SortOrder.UPDATED
        }
        val offset = maxOf(0, page - 1) * 20
        val list = parser.getList(offset = offset, order = sortOrder, filter = MangaListFilter())
        MangaPage(
            items = list.map { it.toSManga() },
            hasNext = list.isNotEmpty(),
        )
    }

    override suspend fun getLatest(page: Int): MangaPage = withContext(Dispatchers.IO) {
        val sortOrder = if (parser.availableSortOrders.contains(SortOrder.UPDATED)) {
            SortOrder.UPDATED
        } else {
            parser.availableSortOrders.firstOrNull() ?: SortOrder.POPULARITY
        }
        val offset = maxOf(0, page - 1) * 20
        val list = parser.getList(offset = offset, order = sortOrder, filter = MangaListFilter())
        MangaPage(
            items = list.map { it.toSManga() },
            hasNext = list.isNotEmpty(),
        )
    }

    override suspend fun search(query: String, filters: FilterList, page: Int): MangaPage = withContext(Dispatchers.IO) {
        val offset = maxOf(0, page - 1) * 20
        val sortOrder = parser.availableSortOrders.firstOrNull() ?: SortOrder.POPULARITY
        val filter = if (query.isNotBlank()) MangaListFilter(query = query) else MangaListFilter()
        val list = parser.getList(offset = offset, order = sortOrder, filter = filter)

        MangaPage(
            items = list.map { it.toSManga() },
            hasNext = list.isNotEmpty(),
        )
    }

    override suspend fun getDetails(manga: SManga): SManga = withContext(Dispatchers.IO) {
        val kotatsuManga = manga.toKotatsuManga()
        val details = parser.getDetails(kotatsuManga)
        details.toSManga()
    }

    override suspend fun getChapters(manga: SManga): List<SChapter> = withContext(Dispatchers.IO) {
        val kotatsuManga = manga.toKotatsuManga()
        val details = parser.getDetails(kotatsuManga)
        val chapters = details.chapters.orEmpty()
        if (chapters.isEmpty()) return@withContext emptyList()

        // Ensure reading order: first chapter first (ascending)
        val sorted = if (chapters.size > 1 && chapters.first().number > chapters.last().number) {
            chapters.reversed()
        } else {
            chapters
        }

        sorted.map { chapter ->
            SChapter(
                url = chapter.url,
                name = chapter.title?.ifBlank { "Chapter ${chapter.numberString()}" } ?: "Chapter ${chapter.numberString()}",
                number = chapter.number,
                uploadedAt = chapter.uploadDate.takeIf { it > 0 },
                scanlator = chapter.scanlator?.takeIf { it.isNotBlank() },
                language = parserSource.locale.takeIf { it.isNotBlank() },
            )
        }
    }

    override suspend fun getPages(chapter: SChapter): List<SPage> = withContext(Dispatchers.IO) {
        val kotatsuChapter = MangaChapter(
            id = 0L,
            title = chapter.name,
            number = chapter.number,
            volume = 0,
            url = chapter.url,
            scanlator = chapter.scanlator.orEmpty(),
            uploadDate = chapter.uploadedAt ?: 0L,
            branch = "",
            source = parserSource,
        )
        val pages = parser.getPages(kotatsuChapter)

        pages.mapIndexed { index, page ->
            SPage.Remote(
                index = index,
                imageUrl = KotatsuUrls.page(id, page.url, page.preview, page.id),
                headers = emptyMap(),
            )
        }
    }

    private fun Manga.toSManga(): SManga = SManga(
        url = url,
        title = title,
        coverUrl = largeCoverUrl ?: coverUrl,
        author = authors.joinToString(", ").takeIf { it.isNotBlank() },
        description = description?.takeIf { it.isNotBlank() },
        genres = tags.map { it.title },
        status = when (state) {
            MangaState.ONGOING -> SManga.Status.Ongoing
            MangaState.FINISHED -> SManga.Status.Completed
            MangaState.PAUSED -> SManga.Status.Hiatus
            MangaState.ABANDONED -> SManga.Status.Cancelled
            else -> SManga.Status.Unknown
        },
        originalLang = parserSource.locale.takeIf { it.isNotBlank() },
    )

    private fun SManga.toKotatsuManga(): Manga = Manga(
        id = 0L,
        title = title,
        altTitles = emptySet(),
        url = url,
        publicUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://${parser.domain}$url",
        rating = 0f,
        contentRating = ContentRating.SAFE,
        coverUrl = coverUrl.orEmpty(),
        tags = genres.map { MangaTag(title = it, key = it, source = parserSource) }.toSet(),
        state = when (status) {
            SManga.Status.Ongoing -> MangaState.ONGOING
            SManga.Status.Completed -> MangaState.FINISHED
            SManga.Status.Hiatus -> MangaState.PAUSED
            SManga.Status.Cancelled -> MangaState.ABANDONED
            SManga.Status.Unknown -> MangaState.ONGOING
        },
        authors = setOfNotNull(author),
        largeCoverUrl = coverUrl,
        description = description.orEmpty(),
        chapters = emptyList(),
        source = parserSource,
    )

    private fun okhttp3.Headers.toMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until size) {
            map[name(i)] = value(i)
        }
        return map
    }
}

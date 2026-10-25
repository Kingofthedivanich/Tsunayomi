package app.tsunayomi.source.api

/** A source that can list, search and read manga. */
interface CatalogSource : Source {
    val supportsLatest: Boolean

    /** The source has its own settings screen (extensions that expose preferences). */
    val hasSettings: Boolean get() = false

    /** Base web page for the source, for opening in WebView or browser. */
    fun webUrl(): String? = null

    /** Web page for the specific manga in this source. */
    fun webUrl(manga: SManga): String? = null

    /** Web headers (e.g. User-Agent, Referer) required for opening the web page in WebView. */
    fun webHeaders(): Map<String, String> = emptyMap()

    fun getFilters(): FilterList = FilterList()

    suspend fun getPopular(page: Int): MangaPage

    suspend fun getLatest(page: Int): MangaPage

    suspend fun search(query: String, filters: FilterList, page: Int): MangaPage

    /**
     * The tags this source can search by, for picking several in its catalog; null when it can't search by tags.
     * By default these are the options of its genre-like filter groups.
     */
    suspend fun searchableTags(): List<String>? = getFilters().tagOptions().ifEmpty { null }

    /**
     * Titles that have all of [tags], or null when this source can't search by one of them. By default the source's
     * filter options with the same names are switched on, which covers extensions and built-in sources without code
     * of their own.
     */
    suspend fun searchByTags(tags: Set<String>, page: Int): MangaPage? {
        val filters = getFilters()
        if (!tags.all { filters.includeTag(it) }) return null
        return search("", filters, page)
    }

    /**
     * Titles by [author], or null when this source can't search. By default it's a text search with the name: sites
     * rarely have an author filter, but most match their search against authors too.
     */
    suspend fun searchByAuthor(author: String, page: Int): MangaPage? = search(author, getFilters(), page)

    suspend fun getDetails(manga: SManga): SManga

    /** Chapters in reading order: the first chapter first. */
    suspend fun getChapters(manga: SManga): List<SChapter>

    suspend fun getPages(chapter: SChapter): List<SPage>
}

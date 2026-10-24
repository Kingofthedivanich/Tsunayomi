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

    suspend fun getDetails(manga: SManga): SManga

    /** Chapters in reading order: the first chapter first. */
    suspend fun getChapters(manga: SManga): List<SChapter>

    suspend fun getPages(chapter: SChapter): List<SPage>
}

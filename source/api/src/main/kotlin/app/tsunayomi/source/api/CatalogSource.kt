package app.tsunayomi.source.api

/** A source that can list, search and read manga. */
interface CatalogSource : Source {
    val supportsLatest: Boolean

    fun getFilters(): FilterList = FilterList()

    suspend fun getPopular(page: Int): MangaPage

    suspend fun getLatest(page: Int): MangaPage

    suspend fun search(query: String, filters: FilterList, page: Int): MangaPage

    suspend fun getDetails(manga: SManga): SManga

    /** Chapters in reading order: the first chapter first. */
    suspend fun getChapters(manga: SManga): List<SChapter>

    suspend fun getPages(chapter: SChapter): List<SPage>
}

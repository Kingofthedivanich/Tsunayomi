package app.tsunayomi.source.api

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CatalogSourceTest {

    private val source = object : CatalogSource {
        override val supportsLatest: Boolean = false
        override val id: Long = 1L
        override val name: String = "Test"
        override val lang: String = "en"
        override suspend fun getPopular(page: Int): MangaPage = MangaPage(emptyList(), false)
        override suspend fun getLatest(page: Int): MangaPage = MangaPage(emptyList(), false)
        override suspend fun search(query: String, filters: FilterList, page: Int): MangaPage = MangaPage(emptyList(), false)
        override suspend fun getDetails(manga: SManga): SManga = manga
        override suspend fun getChapters(manga: SManga): List<SChapter> = emptyList()
        override suspend fun getPages(chapter: SChapter): List<SPage> = emptyList()
    }

    @Test
    fun `default webUrl implementations return null`() {
        assertNull(source.webUrl())
        assertNull(source.webUrl(SManga("/test", "Test", null)))
    }
}

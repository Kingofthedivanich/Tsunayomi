package app.tsunayomi.source.api

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TagSearchTest {

    private class RecordingSource(private val filters: () -> FilterList) : CatalogSource {
        var lastQuery: String? = null
        var lastFilters: FilterList? = null
        override val supportsLatest: Boolean = false
        override val id: Long = 1L
        override val name: String = "Test"
        override val lang: String = "en"
        override fun getFilters(): FilterList = filters()
        override suspend fun getPopular(page: Int): MangaPage = MangaPage(emptyList(), false)
        override suspend fun getLatest(page: Int): MangaPage = MangaPage(emptyList(), false)
        override suspend fun search(query: String, filters: FilterList, page: Int): MangaPage {
            lastQuery = query
            lastFilters = filters
            return MangaPage(emptyList(), false)
        }
        override suspend fun getDetails(manga: SManga): SManga = manga
        override suspend fun getChapters(manga: SManga): List<SChapter> = emptyList()
        override suspend fun getPages(chapter: SChapter): List<SPage> = emptyList()
    }

    @Test
    fun `includeTag switches on a genre option in a group, ignoring case`() {
        val romance = Filter.TriState("Romance")
        val filters = FilterList(Filter.Group("Genres", listOf(Filter.TriState("Action"), romance)))

        assertTrue(filters.includeTag("romance"))
        assertEquals(Filter.TriState.Value.Include, romance.state)
    }

    @Test
    fun `includeTag picks a select value but never the first one`() {
        val select = Filter.Select("Genre", listOf("Any", "Comedy"))

        assertTrue(FilterList(select).includeTag("Comedy"))
        assertEquals(1, select.state)
        assertFalse(FilterList(Filter.Select("Genre", listOf("Any", "Comedy"))).includeTag("Any"))
    }

    @Test
    fun `searchByTags switches on every tag's filter and returns null when one is missing`() = runBlocking {
        val source = RecordingSource { FilterList(Filter.Group("Genres", listOf(Filter.CheckBox("Isekai"), Filter.TriState("Comedy")))) }

        source.searchByTags(setOf("Isekai", "Comedy"), page = 1)
        assertEquals("", source.lastQuery)
        val options = (source.lastFilters!!.single() as Filter.Group<*>).state
        assertTrue((options[0] as Filter.CheckBox).state)
        assertEquals(Filter.TriState.Value.Include, (options[1] as Filter.TriState).state)

        assertNull(source.searchByTags(setOf("Isekai", "Horror"), page = 1))
    }

    @Test
    fun `searchableTags lists the options of filter groups only`() = runBlocking {
        val source = RecordingSource {
            FilterList(
                Filter.CheckBox("Completed only"),
                Filter.Group("Genres", listOf(Filter.TriState("Action"), Filter.CheckBox("Romance"))),
            )
        }

        assertEquals(listOf("Action", "Romance"), source.searchableTags())
        assertNull(RecordingSource { FilterList() }.searchableTags())
    }

    @Test
    fun `searchByAuthor is a text search by default`() = runBlocking {
        val source = RecordingSource { FilterList() }

        source.searchByAuthor("ONE", page = 1)

        assertEquals("ONE", source.lastQuery)
    }
}

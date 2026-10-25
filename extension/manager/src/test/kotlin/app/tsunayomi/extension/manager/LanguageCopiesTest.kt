package app.tsunayomi.extension.manager

import eu.kanade.tachiyomi.source.online.HttpSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LanguageCopiesTest {

    @Suppress("DEPRECATION")
    private class StubSource(override val name: String, override val lang: String) : HttpSource() {
        override val baseUrl: String = "https://example.com"
        override val supportsLatest: Boolean = false

        override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
        override fun popularMangaParse(response: okhttp3.Response) = throw UnsupportedOperationException()
        override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
        override fun latestUpdatesParse(response: okhttp3.Response) = throw UnsupportedOperationException()
        override fun searchMangaRequest(page: Int, query: String, filters: eu.kanade.tachiyomi.source.model.FilterList) = throw UnsupportedOperationException()
        override fun searchMangaParse(response: okhttp3.Response) = throw UnsupportedOperationException()
        override fun mangaDetailsParse(response: okhttp3.Response) = throw UnsupportedOperationException()
        override fun chapterListParse(response: okhttp3.Response) = throw UnsupportedOperationException()
        override fun pageListParse(response: okhttp3.Response) = throw UnsupportedOperationException()
        override fun imageUrlParse(response: okhttp3.Response) = throw UnsupportedOperationException()
    }

    private fun source(name: String, lang: String, icon: String) = ExtensionCatalogSource(StubSource(name, lang), icon)

    @Test
    fun `per-language copies are hidden when the extension has an all-languages source`() {
        val sources = listOf(
            source("3Hentai", "en", "3hentai.png"),
            source("3Hentai", "ja", "3hentai.png"),
            source("3Hentai", "all", "3hentai.png"),
            source("3Hentai", "ru", "3hentai.png"),
        )

        assertEquals(listOf("multi"), sources.withoutLanguageCopies().map { it.lang })
    }

    @Test
    fun `per-language sources stay when there is no all-languages one`() {
        val sources = listOf(
            source("Comic", "en", "comic.png"),
            source("Comic", "ko", "comic.png"),
            // Another extension's all-languages source with the same name doesn't hide them.
            source("Comic", "all", "other.png"),
        )

        assertEquals(listOf("en", "ko", "multi"), sources.withoutLanguageCopies().map { it.lang })
    }
}

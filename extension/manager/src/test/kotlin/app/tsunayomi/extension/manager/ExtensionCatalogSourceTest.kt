package app.tsunayomi.extension.manager

import app.tsunayomi.source.api.SManga
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga as EManga
import eu.kanade.tachiyomi.source.online.HttpSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import rx.Observable

class ExtensionCatalogSourceTest {

    @Suppress("DEPRECATION")
    private val stubHttpSource = object : HttpSource() {
        override val name: String = "Example"
        override val baseUrl: String = "https://example.com"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override fun getMangaUrl(manga: EManga): String = "$baseUrl${manga.url}"

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

    @Test
    fun `webUrl returns baseUrl and manga url for HttpSource`() {
        val source = ExtensionCatalogSource(delegate = stubHttpSource, iconUri = null)

        assertEquals("https://example.com", source.webUrl())
        assertEquals(
            "https://example.com/manga/test",
            source.webUrl(SManga(url = "/manga/test", title = "Test", coverUrl = null)),
        )
    }

    @Test
    fun `webHeaders returns headers with custom User-Agent for HttpSource`() {
        val customHttpSource = object : HttpSource() {
            override val name: String = "Custom"
            override val baseUrl: String = "https://example.com"
            override val lang: String = "en"
            override val supportsLatest: Boolean = true

            override fun headersBuilder(): okhttp3.Headers.Builder =
                okhttp3.Headers.Builder().add("User-Agent", "CustomAgent/2.0")

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

        val source = ExtensionCatalogSource(delegate = customHttpSource, iconUri = null)
        val headers = source.webHeaders()

        assertEquals("CustomAgent/2.0", headers["User-Agent"])
    }
}

package app.tsunayomi.extension.manager

import app.tsunayomi.source.api.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExtensionUrlsTest {
    @Test
    fun `cover address keeps the original url and source id`() {
        val original = "https://example.com/covers/a b.jpg?x=1&y=2"
        val url = ExtensionUrls.cover(42L, original).toHttpUrl()

        assertEquals(ExtensionUrls.HOST, url.host)
        assertEquals(listOf("cover", "42"), url.pathSegments)
        assertEquals(original, ExtensionUrls.original(url.toString()))
    }

    @Test
    fun `page address carries the page index`() {
        val url = ExtensionUrls.page(7L, "/chapter/1/page/3", 2).toHttpUrl()

        assertEquals("/chapter/1/page/3", url.queryParameter("url"))
        assertEquals("2", url.queryParameter("index"))
    }

    @Test
    fun `addresses that are not ours are left alone`() {
        assertEquals("https://site.org/c.png", ExtensionUrls.original("https://site.org/c.png"))
    }

    @Test
    fun `tachiyomi multi-language codes map to the app's`() {
        assertEquals(Source.MULTI_LANG, "all".toAppLang())
        assertEquals(Source.MULTI_LANG, "other".toAppLang())
        assertEquals("pt-BR", "pt-BR".toAppLang())
    }
}

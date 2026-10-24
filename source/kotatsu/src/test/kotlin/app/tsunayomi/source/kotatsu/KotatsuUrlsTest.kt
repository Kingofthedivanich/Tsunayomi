package app.tsunayomi.source.kotatsu

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KotatsuUrlsTest {

    @Test
    fun `page address carries sourceId and query parameters`() {
        val url = KotatsuUrls.page(
            sourceId = 42L,
            pageUrl = "/chapter/1/page/3",
            preview = "https://preview.example.com/3.jpg",
            pageId = 999L,
        ).toHttpUrl()

        assertEquals(KotatsuUrls.HOST, url.host)
        assertEquals(listOf("page", "42"), url.pathSegments)
        assertEquals("/chapter/1/page/3", url.queryParameter("url"))
        assertEquals("https://preview.example.com/3.jpg", url.queryParameter("preview"))
        assertEquals("999", url.queryParameter("id"))
    }

    @Test
    fun `page address without preview or pageId works`() {
        val url = KotatsuUrls.page(
            sourceId = 123L,
            pageUrl = "https://cdn.example.com/image.jpg",
        ).toHttpUrl()

        assertEquals(KotatsuUrls.HOST, url.host)
        assertEquals(listOf("page", "123"), url.pathSegments)
        assertEquals("https://cdn.example.com/image.jpg", url.queryParameter("url"))
        assertEquals(null, url.queryParameter("preview"))
        assertEquals(null, url.queryParameter("id"))
    }
}

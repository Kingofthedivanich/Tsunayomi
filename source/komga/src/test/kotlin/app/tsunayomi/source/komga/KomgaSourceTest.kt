package app.tsunayomi.source.komga

import app.tsunayomi.core.model.KomgaConfig
import app.tsunayomi.core.network.HostAuthRegistry
import app.tsunayomi.source.api.ReadingDirection
import app.tsunayomi.source.api.SChapter
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SPage
import app.tsunayomi.source.api.SourceNotConfiguredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Credentials
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KomgaSourceTest {
    private val server = MockWebServer()
    private val config = MutableStateFlow<KomgaConfig?>(null)
    private lateinit var source: KomgaSource

    @BeforeEach
    fun setUp() {
        server.start()
        // The source relies on the shared client's auth interceptor; recreate it here.
        val registry = HostAuthRegistry()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
            registry.headersForTest(request.url)?.forEach { (k, v) -> builder.header(k, v) }
            chain.proceed(builder.build())
        }.build()
        source = KomgaSource(client, config, registry)
    }

    @AfterEach
    fun tearDown() = server.close()

    private fun enqueue(resource: String) {
        server.enqueue(MockResponse.Builder().body(javaClass.getResource("/komga/$resource")!!.readText()).build())
    }

    private fun configure(apiKey: String = "") {
        config.value = KomgaConfig(url = server.url("/").toString(), username = "reader", password = "secret", apiKey = apiKey)
    }

    @Test
    fun `not configured throws`() = runTest {
        assertThrows<SourceNotConfiguredException> { source.getPopular(1) }
    }

    @Test
    fun `lists series with basic auth`() = runTest {
        configure()
        enqueue("series.json")

        val page = source.getPopular(1)

        val request = server.takeRequest()
        assertEquals("/api/v1/series", request.url.encodedPath)
        assertEquals("0", request.url.queryParameter("page"))
        assertEquals(Credentials.basic("reader", "secret"), request.headers["Authorization"])
        assertTrue(page.hasNext)
        val series = page.items.single()
        assertEquals("Komga Series", series.title)
        assertEquals("From books", series.description)
        assertEquals("W", series.author)
        assertEquals("P", series.artist)
        assertEquals(listOf("Action", "tag1"), series.genres)
        assertEquals(SManga.Status.Ongoing, series.status)
        assertEquals(ReadingDirection.RightToLeft, series.readingDirection)
        assertEquals("ja", series.originalLang)
        assertEquals(server.url("/api/v1/series/S1/thumbnail").toString(), series.coverUrl)
    }

    @Test
    fun `api key replaces basic auth`() = runTest {
        configure(apiKey = "key-123")
        enqueue("series.json")
        source.search("q", app.tsunayomi.source.api.FilterList(), 1)
        val request = server.takeRequest()
        assertEquals("key-123", request.headers["X-API-Key"])
        assertNull(request.headers["Authorization"])
        assertEquals("q", request.url.queryParameter("search"))
    }

    @Test
    fun `books skip ones without pages`() = runTest {
        configure()
        enqueue("books.json")

        val chapters = source.getChapters(SManga(url = "S1", title = "x", coverUrl = null))

        assertEquals("true", server.takeRequest().url.queryParameter("unpaged"))
        assertEquals(listOf("B1", "B3"), chapters.map { it.url })
        assertEquals("Chapter One", chapters[0].name)
        assertEquals("book3.cbz", chapters[1].name)
        assertEquals(1704164645000L, chapters[0].uploadedAt)
        assertEquals(1709510400000L, chapters[1].uploadedAt)
    }

    @Test
    fun `pages point at the page endpoint`() = runTest {
        configure()
        enqueue("pages.json")

        val pages = source.getPages(SChapter(url = "B1", name = "x", number = 1f))

        assertEquals(
            listOf(server.url("/api/v1/books/B1/pages/1").toString(), server.url("/api/v1/books/B1/pages/2").toString()),
            pages.map { (it as SPage.Remote).imageUrl },
        )
        assertTrue(pages.all { (it as SPage.Remote).headers["Accept"] == "image/*" })
    }
}

package app.tsunayomi.source.mangadex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class MangaDexAtHomeReporterTest {
    private val images = MockWebServer()
    private val reports = MockWebServer()
    private lateinit var client: OkHttpClient

    @BeforeEach
    fun setUp() {
        images.start()
        reports.start()
        client = OkHttpClient.Builder()
            .addInterceptor(MangaDexAtHomeReporter(reportUrl = reports.url("/report").toString(), reportClient = { OkHttpClient() }))
            .build()
    }

    @AfterEach
    fun tearDown() {
        images.close()
        reports.close()
    }

    @Test
    fun `reports a fully downloaded at-home image`() {
        images.enqueue(MockResponse.Builder().body("x".repeat(1234)).addHeader("X-Cache", "HIT from node").build())
        reports.enqueue(MockResponse.Builder().code(200).build())

        val url = images.url("/token/data/0123456789abcdef0123456789abcdef/1.png")
        client.newCall(Request.Builder().url(url).build()).execute().use { it.body.bytes() }

        val report = reports.takeRequest(5, TimeUnit.SECONDS)!!
        val body = Json.parseToJsonElement(report.body!!.utf8()).jsonObject
        assertEquals(url.toString(), body["url"]!!.jsonPrimitive.content)
        assertTrue(body["success"]!!.jsonPrimitive.boolean)
        assertTrue(body["cached"]!!.jsonPrimitive.boolean)
        assertEquals(1234L, body["bytes"]!!.jsonPrimitive.long)
    }

    @Test
    fun `reports failed loads`() {
        images.enqueue(MockResponse.Builder().code(500).build())
        reports.enqueue(MockResponse.Builder().code(200).build())

        val url = images.url("/token/data/0123456789abcdef0123456789abcdef/2.png")
        client.newCall(Request.Builder().url(url).build()).execute().close()

        val body = Json.parseToJsonElement(reports.takeRequest(5, TimeUnit.SECONDS)!!.body!!.utf8()).jsonObject
        assertFalse(body["success"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `ignores other requests`() {
        images.enqueue(MockResponse.Builder().body("{}").build())
        client.newCall(Request.Builder().url(images.url("/manga")).build()).execute().close()
        assertNull(reports.takeRequest(500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `matches only at-home image paths outside mangadex org`() {
        val hash = "0123456789abcdef0123456789abcdef"
        assertTrue(MangaDexAtHomeReporter.isAtHomeImage("https://abc.mangadex.network:443/tok/data/$hash/1.png".toHttpUrl()))
        assertTrue(MangaDexAtHomeReporter.isAtHomeImage("https://abc.mangadex.network/tok/data-saver/$hash/1.jpg".toHttpUrl()))
        assertFalse(MangaDexAtHomeReporter.isAtHomeImage("https://uploads.mangadex.org/data/$hash/1.png".toHttpUrl()))
        assertFalse(MangaDexAtHomeReporter.isAtHomeImage("https://uploads.mangadex.org/covers/id/cover.png.256.jpg".toHttpUrl()))
    }
}

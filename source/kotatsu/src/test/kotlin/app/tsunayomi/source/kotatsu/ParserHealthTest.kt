package app.tsunayomi.source.kotatsu

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.io.File
import java.time.Instant

class ParserHealthTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val candidates = listOf(
        // Russian
        MangaParserSource.MANGACHAN,
        MangaParserSource.READMANGA_RU,
        MangaParserSource.SEIMANGA,
        MangaParserSource.MINTMANGA,
        MangaParserSource.SELFMANGA,
        MangaParserSource.DESUME,
        MangaParserSource.COMX,

        // English
        MangaParserSource.LIKEMANGA,
        MangaParserSource.LHTRANSLATION,
        MangaParserSource.TOONILY,

        // Japanese
        MangaParserSource.KLZ9,
        MangaParserSource.RAWXZ,
        MangaParserSource.WELOVEMANGA,

        // Spanish
        MangaParserSource.LEGIONSCANS,
    )

    data class HealthResult(
        val source: MangaParserSource,
        val domain: String,
        val success: Boolean,
        val details: String,
        val isNsfw: Boolean,
    )

    @Test
    fun testParserHealthAndGenerateAllowlist() = runBlocking {
        val isManualRun = System.getProperty("kotatsu.healthcheck") == "true" ||
            System.getenv("KOTATSU_HEALTHCHECK") == "true"

        if (!isManualRun) {
            println("ParserHealthTest: skipped live execution (enable with -Dkotatsu.healthcheck=true)")
            return@runBlocking
        }

        val context = JvmMangaLoaderContext()
        val results = mutableListOf<HealthResult>()
        val passedEntries = mutableListOf<KotatsuAllowlistEntry>()

        println("Starting health checks for ${candidates.size} candidate parsers...")

        for (source in candidates) {
            print("Checking ${source.name} (${source.locale})... ")
            val result = checkSource(context, source)
            results.add(result)

            if (result.success) {
                println("PASSED: ${result.details}")
                passedEntries.add(
                    KotatsuAllowlistEntry(
                        name = source.name,
                        locale = source.locale,
                        domain = result.domain,
                        verifiedAt = Instant.now().toString(),
                        isNsfw = result.isNsfw,
                    )
                )
            } else {
                println("FAILED: ${result.details}")
            }
        }

        // Save allowlist.json
        val rootDir = generateSequence(File(".").canonicalFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").exists() } ?: File(".").canonicalFile
        val moduleDir = File(rootDir, "source/kotatsu")
        val assetsDir = File(moduleDir, "src/main/assets").apply { mkdirs() }

        val allowlistFile = File(assetsDir, "allowlist.json")
        allowlistFile.writeText(json.encodeToString(passedEntries))
        println("Saved ${passedEntries.size} verified parsers to ${allowlistFile.absolutePath}")

        // Save report markdown in source/kotatsu/PARSER_HEALTH_REPORT.md
        val reportFile = File(moduleDir, "PARSER_HEALTH_REPORT.md")

        val reportContent = buildString {
            appendLine("# Kotatsu Parsers Health Report")
            appendLine()
            appendLine("Generated at: ${Instant.now()}")
            appendLine()
            appendLine("## Summary")
            appendLine("- Total candidates tested: ${candidates.size}")
            appendLine("- Passed: ${passedEntries.size}")
            appendLine("- Failed: ${candidates.size - passedEntries.size}")
            appendLine()
            appendLine("## Verified Working Parsers (Added to Allowlist)")
            appendLine("| Name | Locale | Domain | NSFW |")
            appendLine("|---|---|---|---|")
            for (p in passedEntries) {
                appendLine("| `${p.name}` | `${p.locale}` | `${p.domain}` | ${p.isNsfw} |")
            }
            appendLine()
            appendLine("## Detailed Results")
            appendLine("| Source | Status | Result / Error |")
            appendLine("|---|---|---|")
            for (r in results) {
                val statusIcon = if (r.success) "PASS" else "FAIL"
                appendLine("| `${r.source.name}` | $statusIcon | ${r.details.replace("\n", " ")} |")
            }
        }

        reportFile.writeText(reportContent)
        println("Saved health report to ${reportFile.absolutePath}")
    }

    private suspend fun checkSource(context: JvmMangaLoaderContext, source: MangaParserSource): HealthResult {
        var lastResult: HealthResult? = null
        for (attempt in 1..2) {
            val res = runCatching { checkSourceInternal(context, source) }
                .getOrElse { e ->
                    HealthResult(source, "unknown", false, "Exception: ${e.javaClass.simpleName}: ${e.message}", source.contentType == ContentType.HENTAI)
                }
            if (res.success) return res
            lastResult = res
            if (attempt == 1) {
                kotlinx.coroutines.delay(1000)
            }
        }
        return lastResult ?: HealthResult(source, "unknown", false, "Unknown error", source.contentType == ContentType.HENTAI)
    }

    private suspend fun checkSourceInternal(context: JvmMangaLoaderContext, source: MangaParserSource): HealthResult {
        return try {
            val parser = context.newParserInstance(source)
            val domain = parser.domain
            val isNsfw = source.contentType == ContentType.HENTAI

            // 1. Popular manga list
            val sortOrder = parser.availableSortOrders.firstOrNull() ?: SortOrder.POPULARITY
            val mangaList = parser.getList(offset = 0, order = sortOrder, filter = MangaListFilter())
            if (mangaList.isEmpty()) {
                return HealthResult(source, domain, false, "getList returned empty list", isNsfw)
            }
            val manga = mangaList.first()

            // 2. Manga details & chapters
            val details = parser.getDetails(manga)
            val chapters = details.chapters.orEmpty()
            if (chapters.isEmpty()) {
                return HealthResult(source, domain, false, "getDetails found 0 chapters for '${manga.title}'", isNsfw)
            }
            val chapter = chapters.first()

            // 3. Pages
            val pages = parser.getPages(chapter)
            if (pages.isEmpty()) {
                return HealthResult(source, domain, false, "getPages returned empty list for chapter '${chapter.name}'", isNsfw)
            }
            val page = pages.first()

            // 4. Image download
            val imageUrl = parser.getPageUrl(page)
            if (imageUrl.isBlank()) {
                return HealthResult(source, domain, false, "getPageUrl returned empty url", isNsfw)
            }

            val requestBuilder = Request.Builder()
                .url(imageUrl)
                .headers(parser.getRequestHeaders())

            if (requestBuilder.build().header("Referer") == null) {
                requestBuilder.header("Referer", "https://$domain/")
            }

            val response = context.httpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return HealthResult(
                    source,
                    domain,
                    false,
                    "Image fetch returned HTTP ${response.code} for $imageUrl",
                    isNsfw,
                )
            }

            val contentType = response.header("Content-Type").orEmpty()
            val bodyBytes = response.body.bytes()
            if (bodyBytes.isEmpty()) {
                return HealthResult(source, domain, false, "Image body was empty", isNsfw)
            }

            HealthResult(
                source,
                domain,
                true,
                "Manga: '${manga.title}', Chapters: ${chapters.size}, Pages: ${pages.size}, Image: ${bodyBytes.size} bytes (${contentType})",
                isNsfw,
            )
        } catch (e: Throwable) {
            HealthResult(
                source,
                "",
                false,
                "Exception: ${e::class.simpleName}: ${e.message}",
                source.contentType == ContentType.HENTAI,
            )
        }
    }
}

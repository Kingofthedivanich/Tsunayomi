package app.tsunayomi.tools.kotatsu.health

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@Serializable
data class CheckResult(
    val name: String,
    val locale: String,
    val domain: String,
    val ok: Boolean,
    val error: String? = null,
    val isNsfw: Boolean = false,
    val verifiedAt: String = Instant.now().toString(),
)

@Serializable
data class AllowlistEntry(
    val name: String,
    val locale: String,
    val domain: String,
    val verifiedAt: String,
    val isNsfw: Boolean = false,
)

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// The allowlist is read by people too, but health.jsonl must stay one record per line to be resumable.
private val prettyJson = Json(json) { prettyPrint = true }

private val ADULT_TYPES = setOf(
    ContentType.HENTAI,
    ContentType.DOUJINSHI,
    ContentType.IMAGE_SET,
    ContentType.ARTIST_CG,
    ContentType.GAME_CG,
)

// Our own call over kotatsu's classification: these parsers are tagged HENTAI upstream (which also marks
// every title 18+), but the catalogs are general manga.
private val NOT_ADULT = setOf(
    "KLZ9",
)

private val ADULT_DOMAINS = setOf(
    "toonily.com",
    "hentai2read.com",
    "pururin.io",
    "nhentai.net",
    "hitomi.la",
    "asmhentai.com",
    "tsumino.com",
)

suspend fun main() {
    val startTime = System.currentTimeMillis()
    val buildDir = File("tools/kotatsu-health/build")
    buildDir.mkdirs()
    val healthJsonl = File(buildDir, "health.jsonl")

    val doneMap = mutableMapOf<String, CheckResult>()
    if (healthJsonl.exists()) {
        healthJsonl.forEachLine { line ->
            if (line.isNotBlank()) {
                val res = json.decodeFromString<CheckResult>(line)
                doneMap[res.name] = res
            }
        }
    }

    val candidates = MangaParserSource.entries.filter {
        !it.isBroken && it.locale in setOf("en", "ru", "ja", "ko", "zh", "")
    }

    println("Total candidates after filter (!isBroken, locale in en,ru,ja,ko,zh,''): ${candidates.size}")
    println("Already verified in cache: ${doneMap.size}")

    val loaderContext = JvmMangaLoaderContext()
    val gate = Semaphore(32)

    val remaining = candidates.filter { it.name !in doneMap }
    println("Remaining to test: ${remaining.size} (using 32 parallel permits, 45s timeout)")

    coroutineScope {
        remaining.forEach { src ->
            launch(Dispatchers.IO) {
                gate.withPermit {
                    val result = withTimeoutOrNull(45.seconds) {
                        checkSource(src, loaderContext)
                    } ?: CheckResult(
                        name = src.name,
                        locale = src.locale,
                        domain = "",
                        ok = false,
                        error = "timeout 45s"
                    )

                    synchronized(healthJsonl) {
                        healthJsonl.appendText(json.encodeToString(result) + "\n")
                        doneMap[result.name] = result
                    }

                    val status = if (result.ok) "OK (18+: ${result.isNsfw})" else "FAIL: ${result.error}"
                    println("[${doneMap.size}/${candidates.size}] ${src.name} ($status)")
                }
            }
        }
    }

    val elapsedSec = (System.currentTimeMillis() - startTime) / 1000

    val allResults = candidates.map { doneMap[it.name] ?: CheckResult(it.name, it.locale, "", false, "Not evaluated") }
    val passed = allResults.filter { it.ok }
    val failed = allResults.filter { !it.ok }
    val nsfwCount = passed.count { it.isNsfw }

    println("\n=== SUMMARY ===")
    println("Candidates tested: ${candidates.size}")
    println("Passed: ${passed.size}")
    println("Failed: ${failed.size}")
    println("Marked 18+: $nsfwCount")
    println("Duration: ${elapsedSec}s")

    // Group failures
    val failureGroups = failed.groupBy { simplifyError(it.error) }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }

    println("\nTop failure reasons:")
    failureGroups.take(10).forEach { (err, count) ->
        println("  $err: $count")
    }

    // Write allowlist.json
    val allowlistEntries = passed.map {
        AllowlistEntry(
            name = it.name,
            locale = it.locale,
            domain = it.domain,
            verifiedAt = it.verifiedAt,
            isNsfw = it.isNsfw
        )
    }.sortedBy { it.name }

    val rootDir = File(".").let { if (it.canonicalPath.endsWith("kotatsu-health")) it.parentFile.parentFile else it }
    val allowlistFile = File(rootDir, "source/kotatsu/src/main/assets/allowlist.json")
    allowlistFile.parentFile.mkdirs()
    allowlistFile.writeText(prettyJson.encodeToString(allowlistEntries))
    println("\nSaved ${allowlistEntries.size} verified sources to ${allowlistFile.path}")

    // Write PARSER_HEALTH_REPORT.md
    val reportFile = File(rootDir, "source/kotatsu/PARSER_HEALTH_REPORT.md")
    val reportContent = buildString {
        appendLine("# Отчет о проверке парсеров Kotatsu")
        appendLine()
        appendLine("Дата проверки: ${Instant.now()}")
        appendLine("Общее число кандидатов (с учетом фильтра `!isBroken` и языков `en`, `ru`, `ja`, `ko`, `zh`): **${candidates.size}**")
        appendLine("Успешно прошли полную цепочку проверки: **${passed.size}**")
        appendLine("Не прошли проверку: **${failed.size}**")
        appendLine("Помечено как 18+ (NSFW): **$nsfwCount**")
        appendLine("Время полного параллельного прогона (32 потока): **${elapsedSec} сек**")
        appendLine()
        appendLine("## Критерии прохождения")
        appendLine("1. `getList(offset=0, filter=null)` возвращает непустой список тайтлов;")
        appendLine("2. Из первых 3 тайтлов хотя бы у одного `getDetails(manga)` возвращает непустой список глав;")
        appendLine("3. `getPages(chapter)` для последней главы возвращает список страниц;")
        appendLine("4. Первая страница отдается по HTTP (код 200/206 при запросе первых 2 КБ) с валидной сигнатурой изображения (JPEG, PNG, WEBP, GIF);")
        appendLine("5. Определение NSFW: по типу контента (`ADULT_TYPES`), тегам/рейтингу тайтлов и списку доменов.")
        appendLine()
        appendLine("## Топ причин отказов")
        appendLine("| Причина отказа / Категория ошибки | Количество парсеров |")
        appendLine("|---|---|")
        failureGroups.take(10).forEach { (err, count) ->
            appendLine("| `$err` | $count |")
        }
        appendLine()
        appendLine("## Прошедшие парсеры (Allowlist)")
        appendLine("| Имя источника | Локаль | Домен | 18+ (NSFW) |")
        appendLine("|---|---|---|---|")
        allowlistEntries.forEach { e ->
            val nsfwBadge = if (e.isNsfw) "Да (18+)" else "Нет"
            appendLine("| `${e.name}` | `${e.locale}` | `${e.domain}` | $nsfwBadge |")
        }
    }
    reportFile.writeText(reportContent)
    println("Saved report to ${reportFile.path}")
}

private suspend fun checkSource(src: MangaParserSource, context: JvmMangaLoaderContext): CheckResult {
    val parser: MangaParser = try {
        context.newParserInstance(src)
    } catch (e: Throwable) {
        return CheckResult(src.name, src.locale, "", false, "Parser init error: ${e.message}")
    }
    val domain = parser.domain

    // 1. getList
    val sortOrder = parser.availableSortOrders.firstOrNull() ?: org.koitharu.kotatsu.parsers.model.SortOrder.UPDATED
    val list = try {
        parser.getList(offset = 0, order = sortOrder, filter = org.koitharu.kotatsu.parsers.model.MangaListFilter())
    } catch (e: Throwable) {
        return CheckResult(src.name, src.locale, domain, false, "getList failed: ${e.message ?: e::class.simpleName}")
    }
    if (list.isEmpty()) {
        return CheckResult(src.name, src.locale, domain, false, "getList empty")
    }

    // Sample up to 3 titles for details & chapters
    val sampled = list.take(3)
    var chapters: List<MangaChapter> = emptyList()

    for (manga in sampled) {
        try {
            val detailed = parser.getDetails(manga)
            val chs = detailed.chapters.orEmpty()
            if (chs.isNotEmpty()) {
                chapters = chs
                break
            }
        } catch (_: Throwable) {
            // try next
        }
    }

    if (chapters.isEmpty()) {
        return CheckResult(src.name, src.locale, domain, false, "No chapters found in first 3 titles")
    }

    // 3. getPages
    val chapter = chapters.last()
    val pages = try {
        parser.getPages(chapter)
    } catch (e: Throwable) {
        return CheckResult(src.name, src.locale, domain, false, "getPages failed: ${e.message ?: e::class.simpleName}")
    }
    if (pages.isEmpty()) {
        return CheckResult(src.name, src.locale, domain, false, "getPages returned empty list")
    }

    // 4. Check image headers & bytes
    val page = pages.first()
    val pageUrl = try {
        parser.getPageUrl(page)
    } catch (e: Throwable) {
        return CheckResult(src.name, src.locale, domain, false, "getPageUrl failed: ${e.message ?: e::class.simpleName}")
    }

    try {
        val reqBuilder = Request.Builder().url(pageUrl)
        try {
            val headers = parser.getRequestHeaders()
            reqBuilder.headers(headers)
        } catch (_: Throwable) {
            // fallback to default
        }
        reqBuilder.header("Range", "bytes=0-2047")

        context.httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (resp.code !in 200..206) {
                return CheckResult(src.name, src.locale, domain, false, "Image HTTP ${resp.code}")
            }
            val body = resp.body ?: return CheckResult(src.name, src.locale, domain, false, "Image body empty")
            val bytes = body.byteStream().readNBytes(16)
            if (!isValidImageMagicBytes(bytes)) {
                return CheckResult(src.name, src.locale, domain, false, "Image magic bytes invalid")
            }
        }
    } catch (e: Throwable) {
        return CheckResult(src.name, src.locale, domain, false, "Image fetch failed: ${e.message ?: e::class.simpleName}")
    }

    // 5. Check 18+
    val isNsfw = src.name !in NOT_ADULT && (
        src.contentType in ADULT_TYPES ||
            ADULT_DOMAINS.any { domain.contains(it, ignoreCase = true) } ||
            sampled.any { m -> m.isNsfw || m.contentRating == ContentRating.ADULT }
        )

    return CheckResult(
        name = src.name,
        locale = src.locale,
        domain = domain,
        ok = true,
        error = null,
        isNsfw = isNsfw
    )
}

private fun isValidImageMagicBytes(bytes: ByteArray): Boolean {
    if (bytes.size < 4) return false
    // JPEG: FF D8
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return true
    // PNG: 89 50 4E 47
    if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return true
    // GIF: 47 49 46 38 ("GIF8")
    if (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == '8'.code.toByte()) return true
    // WEBP: "RIFF....WEBP"
    if (bytes.size >= 12 &&
        bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
        bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
    ) return true
    return false
}

private fun simplifyError(err: String?): String {
    if (err == null) return "Unknown"
    val lower = err.lowercase()
    return when {
        "timeout" in lower -> "Connection / Read Timeout"
        "cloudflaredetectionexception" in lower || "cloudflare" in lower || "cf_clearance" in lower -> "Cloudflare Bot Protection"
        "403" in lower -> "HTTP 403 Forbidden"
        "404" in lower -> "HTTP 404 Not Found"
        "500" in lower || "502" in lower || "503" in lower -> "HTTP 5xx Server Error"
        "image http" in lower -> err
        "image magic bytes" in lower -> "Invalid Image Magic Bytes (HTML / Corrupted)"
        "no chapters" in lower -> "No Chapters in First 3 Titles"
        "getlist empty" in lower -> "Empty Catalog List"
        "getlist failed" in lower -> "Catalog Parse Error"
        "getpages failed" in lower || "getpages returned empty" in lower -> "Chapter Pages Parse Error"
        "ssl" in lower || "cert" in lower -> "SSL / Certificate Error"
        "unknownhost" in lower || "failed to connect" in lower -> "Host Unreachable / DNS Error"
        else -> err.take(40)
    }
}

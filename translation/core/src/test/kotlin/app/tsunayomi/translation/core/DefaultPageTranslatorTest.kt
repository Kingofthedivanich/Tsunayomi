package app.tsunayomi.translation.core

import android.graphics.Bitmap
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.translation.api.JapaneseOcrProvider
import app.tsunayomi.translation.api.NormalizedBox
import app.tsunayomi.translation.api.PageRequest
import app.tsunayomi.translation.api.RecognizedBlock
import app.tsunayomi.translation.api.SourceText
import app.tsunayomi.translation.api.TextRecognizer
import app.tsunayomi.translation.api.TranslatedRegion
import app.tsunayomi.translation.api.TranslationBatch
import app.tsunayomi.translation.api.TranslationContext
import app.tsunayomi.translation.api.TranslationException
import app.tsunayomi.translation.api.Translator
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefaultPageTranslatorTest {
    // Two blocks per page, far apart so bubble grouping keeps them separate.
    private var recognizeCalls = 0
    private val recognizer = object : TextRecognizer {
        override suspend fun recognize(page: Bitmap, sourceLang: String?): List<RecognizedBlock> {
            recognizeCalls++
            return listOf(
                RecognizedBlock(NormalizedBox(0.1f, 0.1f, 0.4f, 0.15f), firstText),
                RecognizedBlock(NormalizedBox(0.1f, 0.6f, 0.4f, 0.65f), "World"),
            )
        }
    }
    private var firstText = "Hello"

    private val japaneseOcr = object : JapaneseOcrProvider {
        var available = false
        val reads = mutableListOf<NormalizedBox>()
        override fun isAvailable() = available
        override suspend fun recognize(page: Bitmap, box: NormalizedBox): String {
            reads += box
            return "おはよう"
        }
    }

    private class FakeTranslator(
        override val engine: TranslationEngine,
        override val batchesPages: Boolean,
        override val cacheVersion: Int = 1,
        var failure: TranslationException? = null,
        var newTerms: Map<String, String> = emptyMap(),
    ) : Translator {
        val calls = mutableListOf<Pair<List<SourceText>, TranslationContext>>()
        override suspend fun translate(
            texts: List<SourceText>,
            sourceLang: String?,
            targetLang: String,
            context: TranslationContext,
        ): TranslationBatch {
            calls += texts to context
            failure?.let { throw it }
            return TranslationBatch(texts.map { "${it.text}→$targetLang(${engine.name})" }, newTerms)
        }
    }

    private class MemoryCache : TranslationCache {
        val entries = mutableMapOf<Triple<String, String, String>, List<TranslatedRegion>>()
        override suspend fun get(pageKey: String, targetLang: String, engineKey: String) = entries[Triple(pageKey, targetLang, engineKey)]
        override suspend fun put(pageKey: String, targetLang: String, engineKey: String, regions: List<TranslatedRegion>) {
            entries[Triple(pageKey, targetLang, engineKey)] = regions
        }
    }

    private class MemoryGlossary : GlossaryStore {
        val terms = mutableMapOf<Pair<Long, String>, MutableMap<String, String>>()
        override suspend fun terms(mangaId: Long, targetLang: String) = terms[mangaId to targetLang].orEmpty().toMap()
        override suspend fun add(mangaId: Long, targetLang: String, terms: Map<String, String>) {
            val existing = this.terms.getOrPut(mangaId to targetLang) { mutableMapOf() }
            terms.forEach { (k, v) -> existing.putIfAbsent(k, v) }
        }
        override fun observe(mangaId: Long): Flow<List<GlossaryEntry>> = emptyFlow()
        override suspend fun delete(mangaId: Long, entry: GlossaryEntry) = Unit
        override suspend fun clear(mangaId: Long) = Unit
    }

    private val mlKit = FakeTranslator(TranslationEngine.MlKit, batchesPages = false)
    private val gemini = FakeTranslator(TranslationEngine.Gemini, batchesPages = true, cacheVersion = 3)
    private val cache = MemoryCache()
    private val glossary = MemoryGlossary()
    private var engine = TranslationEngine.Gemini
    private val bitmap: Bitmap = mockk(relaxed = true)

    private fun translator(vararg translators: Translator = arrayOf(mlKit, gemini)) =
        DefaultPageTranslator(recognizer, translators.toSet(), cache, glossary, { engine to "ru" }, japaneseOcr)

    private fun pages(vararg keys: String) = keys.map { PageRequest(it) { bitmap } }

    @Test
    fun `japanese bubbles are read again by the japanese model and cached apart`() = runTest {
        japaneseOcr.available = true
        firstText = "おは よう"

        val result = translator().translate(pages("p1"), "ja", mangaId = null)

        assertEquals(1, japaneseOcr.reads.size) // "World" has no kana or kanji
        assertEquals(listOf("おはよう", "World"), result.single().regions.map { it.sourceText })
        assertTrue(cache.entries.keys.any { it.third == "Gemini:3:p2:mocr1:ja" })
    }

    @Test
    fun `the japanese model is not used for other languages`() = runTest {
        japaneseOcr.available = true
        firstText = "漢字"

        translator().translate(pages("p1"), "en", mangaId = null)

        assertEquals(0, japaneseOcr.reads.size)
        assertTrue(cache.entries.keys.any { it.third == "Gemini:3:p2:en" })
    }

    @Test
    fun `a run of pages goes to a batch engine in one request with page numbers`() = runTest {
        val result = translator().translate(pages("p1", "p2", "p3"), "en", mangaId = 7)

        assertEquals(1, gemini.calls.size)
        assertEquals(listOf(1, 1, 2, 2, 3, 3), gemini.calls.single().first.map { it.page })
        assertEquals(3, result.size)
        assertEquals(listOf("Hello→ru(Gemini)", "World→ru(Gemini)"), result[1].regions.map { it.translatedText })
        assertTrue(Triple("p2", "ru", "Gemini:3:p2:en") in cache.entries)
    }

    @Test
    fun `offline engines are called page by page`() = runTest {
        engine = TranslationEngine.MlKit
        translator().translate(pages("p1", "p2"), "en", mangaId = 7)
        assertEquals(2, mlKit.calls.size)
    }

    @Test
    fun `cached pages are skipped and not loaded again`() = runTest {
        val pageTranslator = translator()
        pageTranslator.translate(pages("p1"), "en", mangaId = 7)
        recognizeCalls = 0

        pageTranslator.translate(
            listOf(PageRequest("p1") { error("must not load a cached page") }, PageRequest("p2") { bitmap }),
            "en",
            mangaId = 7,
        )

        assertEquals(1, recognizeCalls)
        assertEquals(listOf("Hello", "World"), gemini.calls.last().first.map { it.text })
    }

    @Test
    fun `glossary terms are collected and sent with the next batch`() = runTest {
        gemini.newTerms = mapOf("Kenta" to "Кента")
        val pageTranslator = translator()

        pageTranslator.translate(pages("p1"), "ja", mangaId = 7)
        gemini.newTerms = mapOf("Kenta" to "Кэнта", "Hana" to "Хана")
        pageTranslator.translate(pages("p2"), "ja", mangaId = 7)

        assertEquals(mapOf("Kenta" to "Кента"), gemini.calls[1].second.glossary)
        // The first translation of a name sticks.
        assertEquals(mapOf("Kenta" to "Кента", "Hana" to "Хана"), glossary.terms(7, "ru"))
    }

    @Test
    fun `falls back to ML Kit page by page and reports why`() = runTest {
        gemini.failure = TranslationException.MissingApiKey(TranslationEngine.Gemini)

        val result = translator().translate(pages("p1", "p2"), "en", mangaId = 7)

        assertTrue(result.all { it.engine == TranslationEngine.MlKit && it.fallbackReason is TranslationException.MissingApiKey })
        assertEquals(2, mlKit.calls.size)
        assertNull(cache.entries[Triple("p1", "ru", "Gemini:3:p2:en")])
    }

    @Test
    fun `offline engine errors are not swallowed`() = runTest {
        engine = TranslationEngine.MlKit
        mlKit.failure = TranslationException.UnsupportedLanguage("xx")
        assertThrows<TranslationException.UnsupportedLanguage> { translator().translate(pages("p"), "xx", mangaId = 7) }
    }

    @Test
    fun `overlapping requests translate each page once`() = runTest {
        val pageTranslator = translator()
        listOf(
            async { pageTranslator.translate(pages("p1", "p2"), "en", mangaId = 7) },
            async { pageTranslator.translate(pages("p2", "p3"), "en", mangaId = 7) },
        ).awaitAll()
        // Recognition may overlap, but the shared page is only sent to the translator once.
        assertEquals(6, gemini.calls.sumOf { it.first.size })
    }

    @Test
    fun `changing the prompt version invalidates the cache`() = runTest {
        translator().translate(pages("p"), "en", mangaId = 7)
        val updated = FakeTranslator(TranslationEngine.Gemini, batchesPages = true, cacheVersion = 4)
        translator(mlKit, updated).translate(pages("p"), "en", mangaId = 7)
        assertEquals(1, updated.calls.size)
    }

    @Test
    fun `large runs are split into several requests without splitting pages`() {
        val block = RecognizedBlock(NormalizedBox(0f, 0f, 1f, 1f), "x".repeat(1_000))
        val pageTranslator = translator()
        // 30 pages × 1000 chars exceeds one request; each chunk must hold whole pages.
        val chunks = pageTranslator.chunk((0 until 30).associateWith { listOf(block) })
        assertTrue(chunks.size > 1)
        assertEquals((0 until 30).toList(), chunks.flatMap { it.keys })
    }
}

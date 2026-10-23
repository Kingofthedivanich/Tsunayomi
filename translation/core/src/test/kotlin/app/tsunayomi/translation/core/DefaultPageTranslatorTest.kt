package app.tsunayomi.translation.core

import android.graphics.Bitmap
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.translation.api.NormalizedBox
import app.tsunayomi.translation.api.RecognizedBlock
import app.tsunayomi.translation.api.TextRecognizer
import app.tsunayomi.translation.api.TranslatedRegion
import app.tsunayomi.translation.api.TranslationException
import app.tsunayomi.translation.api.Translator
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefaultPageTranslatorTest {
    private val box = NormalizedBox(0.1f, 0.1f, 0.4f, 0.2f)
    private var recognizeCalls = 0
    private val recognizer = object : TextRecognizer {
        override suspend fun recognize(page: Bitmap, sourceLang: String?): List<RecognizedBlock> {
            recognizeCalls++
            return listOf(RecognizedBlock(box, "Hello"), RecognizedBlock(box, "World"))
        }
    }

    private class FakeTranslator(
        override val engine: TranslationEngine,
        var failure: TranslationException? = null,
        override val cacheVersion: Int = 1,
    ) : Translator {
        var calls = 0
        override suspend fun translate(texts: List<String>, sourceLang: String?, targetLang: String): List<String> {
            calls++
            failure?.let { throw it }
            return texts.map { "$it→$targetLang(${engine.name})" }
        }
    }

    private class MemoryCache : TranslationCache {
        val entries = mutableMapOf<Triple<String, String, String>, List<TranslatedRegion>>()
        override suspend fun get(pageKey: String, targetLang: String, engineKey: String) = entries[Triple(pageKey, targetLang, engineKey)]
        override suspend fun put(pageKey: String, targetLang: String, engineKey: String, regions: List<TranslatedRegion>) {
            entries[Triple(pageKey, targetLang, engineKey)] = regions
        }
    }

    private val mlKit = FakeTranslator(TranslationEngine.MlKit)
    private val gemini = FakeTranslator(TranslationEngine.Gemini, cacheVersion = 3)
    private val cache = MemoryCache()
    private var engine = TranslationEngine.Gemini
    private val bitmap: Bitmap = mockk(relaxed = true)

    private fun translator() = DefaultPageTranslator(recognizer, setOf(mlKit, gemini), cache) { engine to "ru" }

    @Test
    fun `translates with the chosen engine and caches the result`() = runTest {
        val pageTranslator = translator()

        val first = pageTranslator.translate("page-1", "en") { bitmap }
        val second = pageTranslator.translate("page-1", "en") { error("must not load the page again") }

        assertEquals(TranslationEngine.Gemini, first.engine)
        assertEquals(listOf("Hello→ru(Gemini)", "World→ru(Gemini)"), first.regions.map { it.translatedText })
        assertEquals(first.regions, second.regions)
        assertEquals(1, recognizeCalls)
        assertEquals(1, gemini.calls)
        assertTrue(Triple("page-1", "ru", "Gemini:3:p2:en") in cache.entries)
    }

    @Test
    fun `falls back to ML Kit and reports why`() = runTest {
        gemini.failure = TranslationException.MissingApiKey(TranslationEngine.Gemini)

        val result = translator().translate("page-1", "en") { bitmap }

        assertEquals(TranslationEngine.MlKit, result.engine)
        assertTrue(result.fallbackReason is TranslationException.MissingApiKey)
        assertEquals("Hello→ru(MlKit)", result.regions.first().translatedText)
        // Not cached as Gemini, so Gemini is retried once the user adds a key.
        assertNull(cache.entries[Triple("page-1", "ru", "Gemini:3:p2:en")])
    }

    @Test
    fun `offline engine errors are not swallowed`() = runTest {
        engine = TranslationEngine.MlKit
        mlKit.failure = TranslationException.UnsupportedLanguage("xx")
        assertThrows<TranslationException.UnsupportedLanguage> { translator().translate("p", "xx") { bitmap } }
    }

    @Test
    fun `concurrent requests for one page do the work once`() = runTest {
        val pageTranslator = translator()
        listOf(async { pageTranslator.translate("p", "en") { bitmap } }, async { pageTranslator.translate("p", "en") { bitmap } })
            .awaitAll()
        assertEquals(1, recognizeCalls)
        assertEquals(1, gemini.calls)
    }

    @Test
    fun `changing the prompt version invalidates the cache`() = runTest {
        translator().translate("p", "en") { bitmap }
        val updated = FakeTranslator(TranslationEngine.Gemini, cacheVersion = 4)
        DefaultPageTranslator(recognizer, setOf(mlKit, updated), cache) { engine to "ru" }.translate("p", "en") { bitmap }
        assertEquals(1, updated.calls)
    }
}

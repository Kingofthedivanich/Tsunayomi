package app.tsunayomi.translation.api

import android.graphics.Bitmap
import app.tsunayomi.core.model.TranslationEngine

/** Finds and reads the text on a page. */
interface TextRecognizer {
    /** [sourceLang] (ISO 639-1) picks the script model; null means "detect". */
    suspend fun recognize(page: Bitmap, sourceLang: String?): List<RecognizedBlock>
}

/** One text to translate. [page] numbers pages within a batch so the model sees where each page starts. */
data class SourceText(val text: String, val page: Int = 1)

/** What the translator should keep consistent: established translations of names and terms (source → target). */
data class TranslationContext(val glossary: Map<String, String> = emptyMap())

data class TranslationBatch(
    /** One translation per input text, in order. */
    val translations: List<String>,
    /** Names and terms first seen in this batch, to be kept for the rest of the series. */
    val newTerms: Map<String, String> = emptyMap(),
)

interface Translator {
    val engine: TranslationEngine

    /** Part of the cache key; bump when output changes (e.g. a new prompt) so old translations are redone. */
    val cacheVersion: Int get() = 1

    /** Settings that change the output (e.g. the chosen models), also part of the cache key. */
    suspend fun cacheTag(): String = ""

    /**
     * True for engines that do better with many pages in one call (LLMs: shared context, fewer requests).
     * Others are called page by page.
     */
    val batchesPages: Boolean get() = false

    suspend fun translate(
        texts: List<SourceText>,
        sourceLang: String?,
        targetLang: String,
        context: TranslationContext = TranslationContext(),
    ): TranslationBatch
}

/** A page to translate: its stable cache key and how to load its image (only called on a cache miss). */
data class PageRequest(val key: String, val load: suspend () -> Bitmap)

/** Runs the pipeline for a run of pages of one chapter: recognize, translate (batched when the engine likes it), cache. */
interface PageTranslator {
    /**
     * Returns one result per page, in order. [mangaId] scopes the glossary; null disables it.
     * If the chosen online engine fails for a fixable reason, pages are translated offline and the reason reported.
     */
    suspend fun translate(pages: List<PageRequest>, sourceLang: String?, mangaId: Long?): List<PageTranslation>
}

package app.tsunayomi.translation.api

import android.graphics.Bitmap
import app.tsunayomi.core.model.TranslationEngine

/** Finds and reads the text on a page. */
interface TextRecognizer {
    /** [sourceLang] (ISO 639-1) picks the script model; null means "probably CJK", which also reads Latin. */
    suspend fun recognize(page: Bitmap, sourceLang: String?): List<RecognizedBlock>
}

/**
 * Translates a batch of texts in one go. Batches let LLM engines see the whole page at once,
 * which keeps names and tone consistent and costs one request instead of one per bubble.
 */
interface Translator {
    val engine: TranslationEngine

    /** Part of the cache key; bump when output changes (e.g. a new prompt) so old translations are redone. */
    val cacheVersion: Int get() = 1

    /** Returns one translation per input, in order. [sourceLang] may be null when unknown. */
    suspend fun translate(texts: List<String>, sourceLang: String?, targetLang: String): List<String>
}

/** Runs the whole pipeline for one page — recognize, translate, cache — with fallback to the offline engine. */
interface PageTranslator {
    /**
     * [pageKey] identifies the page image (its URL or URI). [loadPage] is only called on a cache miss.
     */
    suspend fun translate(
        pageKey: String,
        sourceLang: String?,
        loadPage: suspend () -> Bitmap,
    ): PageTranslation
}

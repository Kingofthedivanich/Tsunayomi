package app.tsunayomi.translation.api

import android.graphics.Bitmap

/** Finds speech bubbles and text areas on a page. */
interface TextDetector {
    suspend fun detect(page: Bitmap): List<TextBlock>
}

/** Recognizes the text inside detected blocks. Returns one string per block, in the same order. */
interface OcrEngine {
    /** ISO 639-1 codes this engine reads well. */
    val supportedLanguages: Set<String>

    suspend fun recognize(page: Bitmap, blocks: List<TextBlock>, lang: String?): List<String>
}

/**
 * Translates a batch of texts. Batches let LLM engines see the whole page or chapter at once,
 * which keeps names and tone consistent and costs one request instead of one per bubble.
 */
interface Translator {
    val engine: TranslatorEngine

    suspend fun translate(texts: List<String>, request: TranslationRequest): List<String>
}

/** Runs the whole pipeline for one page: detect → OCR → translate. Results are cached by implementations. */
interface PageTranslator {
    suspend fun translate(page: Bitmap, request: TranslationRequest): List<TranslatedRegion>
}

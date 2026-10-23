package app.tsunayomi.translation.api

import app.tsunayomi.core.model.TranslationEngine
import kotlinx.serialization.Serializable

/**
 * Box in normalized page coordinates: 0..1 of the page width/height, so it fits any rendering
 * of the page (zoomed, downscaled for OCR, full-width webtoon strip).
 */
@Serializable
data class NormalizedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** A block of text found on the page: a speech bubble or a piece of one. */
data class RecognizedBlock(
    val box: NormalizedBox,
    val text: String,
    /** Average height of one text line, normalized like [box]; used to tell neighbouring pieces of one bubble apart. */
    val lineHeight: Float = box.height,
)

@Serializable
data class TranslatedRegion(
    val box: NormalizedBox,
    val sourceText: String,
    val translatedText: String,
)

data class PageTranslation(
    val regions: List<TranslatedRegion>,
    /** The engine that actually produced the text; differs from the chosen one after a fallback. */
    val engine: TranslationEngine,
    /** Why the chosen engine was replaced by the offline one, if it was. */
    val fallbackReason: TranslationException? = null,
)

/** Failures the UI explains to the user rather than showing a raw message. */
sealed class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingApiKey(val engine: TranslationEngine) : TranslationException("$engine needs an API key")
    class InvalidApiKey(val engine: TranslationEngine) : TranslationException("$engine rejected the API key")
    class QuotaExceeded(val engine: TranslationEngine) : TranslationException("$engine quota exceeded")
    class UnsupportedLanguage(val lang: String) : TranslationException("Language not supported: $lang")
    class Network(cause: Throwable) : TranslationException("Network error: ${cause.message}", cause)
    class Engine(message: String, cause: Throwable? = null) : TranslationException(message, cause)
}

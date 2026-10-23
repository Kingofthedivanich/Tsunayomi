package app.tsunayomi.translation.api

/** Axis-aligned box in source image pixels. */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** A detected block of text on a page: a speech bubble or free-floating text. */
data class TextBlock(
    val box: BoundingBox,
    /** Outline of the bubble in image pixels as x0,y0,x1,y1,…; null if only the box is known. */
    val polygon: List<Float>? = null,
    val isBubble: Boolean,
    val vertical: Boolean = false,
)

/** A text block after OCR and translation, ready to be drawn over the page. */
data class TranslatedRegion(
    val block: TextBlock,
    val sourceText: String,
    val translatedText: String,
)

/** Everything the pipeline needs to know besides the image itself. */
data class TranslationRequest(
    /** ISO 639-1 code; null means detect automatically. */
    val sourceLang: String?,
    val targetLang: String,
    val readingDirection: ReadingOrder,
)

enum class ReadingOrder { LeftToRight, RightToLeft }

/** Engines the user can pick in settings. Gemini Nano was dropped: too few devices to test on. */
enum class TranslatorEngine(val requiresApiKey: Boolean, val requiresNetwork: Boolean) {
    MlKit(requiresApiKey = false, requiresNetwork = false),
    OfflineModel(requiresApiKey = false, requiresNetwork = false),
    Gemini(requiresApiKey = true, requiresNetwork = true),
    OpenAiCompatible(requiresApiKey = true, requiresNetwork = true),
    DeepL(requiresApiKey = true, requiresNetwork = true),
}

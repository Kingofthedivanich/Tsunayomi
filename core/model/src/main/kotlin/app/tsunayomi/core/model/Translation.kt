package app.tsunayomi.core.model

/** Engines the user can pick. Gemini Nano was dropped (too few devices to test on), Microsoft too. */
enum class TranslationEngine(val requiresApiKey: Boolean, val implemented: Boolean) {
    MlKit(requiresApiKey = false, implemented = true),
    Gemini(requiresApiKey = true, implemented = true),
    OpenAiCompatible(requiresApiKey = true, implemented = false),
    DeepL(requiresApiKey = true, implemented = false),
    OfflineModel(requiresApiKey = false, implemented = false),
}

data class GeminiConfig(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
) {
    companion object {
        /** Cheapest fast model at the time of writing; the user can change it in settings. */
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    }
}

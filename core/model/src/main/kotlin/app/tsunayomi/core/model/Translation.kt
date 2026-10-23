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
    /** Tried in order; when one runs out of its free quota the next takes over. */
    val models: List<String> = DEFAULT_MODELS,
) {
    companion object {
        /**
         * Free-tier quotas are per model, so a chain multiplies the daily budget:
         * 500 + 500 requests/day for the Flash Lite models, then Gemma with a large daily cap but a tight
         * tokens-per-minute limit.
         */
        val DEFAULT_MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemma-4-31b-it")
    }
}

package app.tsunayomi.core.model

/** Engines the user can pick. Gemini Nano was dropped (too few devices to test on), Microsoft too. */
enum class TranslationEngine(val requiresApiKey: Boolean, val implemented: Boolean) {
    MlKit(requiresApiKey = false, implemented = true),
    Gemini(requiresApiKey = true, implemented = true),
    OpenAiCompatible(requiresApiKey = false, implemented = true),
    DeepL(requiresApiKey = true, implemented = true),
    OfflineModel(requiresApiKey = false, implemented = false),
}

data class DeepLConfig(
    val apiKey: String = "",
) {
    val isFreeTier: Boolean get() = apiKey.endsWith(":fx")
    val endpoint: String get() = if (isFreeTier) FREE_ENDPOINT else PRO_ENDPOINT

    companion object {
        const val FREE_ENDPOINT = "https://api-free.deepl.com/v2"
        const val PRO_ENDPOINT = "https://api.deepl.com/v2"
    }
}

data class DeepLUsage(
    val characterCount: Long = 0L,
    val characterLimit: Long = 500_000L,
) {
    val percentage: Int get() = if (characterLimit > 0) ((characterCount * 100) / characterLimit).toInt() else 0
}

data class OpenAiConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"

        val PRESET_DEEPSEEK = OpenAiConfig("https://api.deepseek.com/v1", "", "deepseek-chat")
        val PRESET_OPENROUTER = OpenAiConfig("https://openrouter.ai/api/v1", "", "deepseek/deepseek-chat")
        val PRESET_GROQ = OpenAiConfig("https://api.groq.com/openai/v1", "", "llama-3.3-70b-versatile")
        val PRESET_OLLAMA = OpenAiConfig("http://192.168.1.100:11434/v1", "", "qwen2.5:7b")
        val PRESET_OPENAI = OpenAiConfig("https://api.openai.com/v1", "", "gpt-4o-mini")
    }
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

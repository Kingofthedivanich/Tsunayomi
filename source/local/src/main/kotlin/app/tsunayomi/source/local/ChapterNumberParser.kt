package app.tsunayomi.source.local

/** Guesses a chapter number from a file or folder name such as "Vol.2 Ch.15 - Title" or "第12話". */
internal object ChapterNumberParser {
    private const val NUMBER = """(\d+(?:[.,]\d+)?)"""

    private val chapterKeyword = Regex(
        """(?:chapter|chap|ch|глава|гл|episode|ep|c|第)\.?\s*$NUMBER""",
        RegexOption.IGNORE_CASE,
    )
    private val volumeKeyword = Regex("""(?:volume|vol|v|том|т)\.?\s*\d+(?:[.,]\d+)?""", RegexOption.IGNORE_CASE)
    private val anyNumber = Regex(NUMBER)

    fun parse(name: String): Float {
        val base = name.substringBeforeLast('.').takeIf { name.substringAfterLast('.').length in 1..4 } ?: name
        chapterKeyword.find(base)?.let { return it.groupValues[1].toNumber() }
        // No keyword: take the last number that is not a volume number.
        val withoutVolume = volumeKeyword.replace(base, " ")
        return anyNumber.findAll(withoutVolume).lastOrNull()?.groupValues?.get(1)?.toNumber() ?: UNKNOWN
    }

    private fun String.toNumber(): Float = replace(',', '.').toFloat()

    const val UNKNOWN = -1f
}

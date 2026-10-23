package app.tsunayomi.translation.api

/** Turns OCR lines of one block into a sentence the translator can understand. */
object TextJoining {
    fun join(lines: List<String>): String = lines
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .fold(StringBuilder()) { acc, line ->
            when {
                acc.isEmpty() -> acc.append(line)
                // A word hyphenated across lines: "trans-" + "late" -> "translate".
                acc.endsWith("-") && line.first().isLetter() -> acc.setLength(acc.length - 1).let { acc.append(line) }
                // CJK has no spaces between words; a line break is not a word boundary.
                isCjk(acc.last()) || isCjk(line.first()) -> acc.append(line)
                else -> acc.append(' ').append(line)
            }
        }
        .toString()

    /**
     * Drops noise: stray punctuation, a lone Latin letter, and digits-only text such as page numbers.
     * A lone CJK character is kept, it can be a whole word.
     */
    fun isMeaningful(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        return when {
            letters.isEmpty() -> false
            letters.length == 1 -> isCjk(letters.first())
            else -> true
        }
    }

    /** Share of CJK characters among letters; tells a Japanese page from a Latin-script one. */
    fun cjkRatio(text: String): Float {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return 0f
        return letters.count(::isCjk).toFloat() / letters.length
    }

    /**
     * Comic lettering is usually ALL CAPS, which machine translators read as acronyms.
     * Mostly-uppercase text becomes sentence case; normal text is left alone.
     */
    fun normalizeCase(text: String): String {
        val cased = text.filter { it.isLetter() && (it.isUpperCase() || it.isLowerCase()) }
        if (cased.length < 4 || cased.count { it.isUpperCase() } < cased.length * 0.8) return text
        val lower = text.lowercase()
        val result = StringBuilder(lower.length)
        var capitalizeNext = true
        for (c in lower) {
            if (capitalizeNext && c.isLetter()) {
                result.append(c.uppercaseChar())
                capitalizeNext = false
            } else {
                result.append(c)
            }
            if (c == '.' || c == '!' || c == '?' || c == '…') capitalizeNext = true
        }
        return result.toString()
    }

    fun isCjk(c: Char): Boolean = when (Character.UnicodeScript.of(c.code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL,
        -> true
        else -> false
    }
}

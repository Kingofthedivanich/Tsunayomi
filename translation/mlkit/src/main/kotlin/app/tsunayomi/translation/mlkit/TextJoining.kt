package app.tsunayomi.translation.mlkit

/** Turns OCR lines of one block into a sentence the translator can understand. */
internal object TextJoining {
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

    /** Drops noise such as stray punctuation or a lone Latin letter; keeps a lone CJK character, which can be a word. */
    fun isMeaningful(text: String): Boolean {
        val letters = text.filter { it.isLetterOrDigit() }
        return when {
            letters.isEmpty() -> false
            letters.length == 1 -> isCjk(letters.first())
            else -> true
        }
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

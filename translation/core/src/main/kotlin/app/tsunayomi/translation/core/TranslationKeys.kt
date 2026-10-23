package app.tsunayomi.translation.core

/**
 * Cache identities shared by the reader and the downloader, so a chapter translated while downloading
 * is found again when it is read offline.
 */
object TranslationKeys {
    /** Remote page URLs can change between sessions (MangaDex@Home), so pages are keyed by chapter and index. */
    fun page(sourceId: Long, chapterUrl: String, index: Int): String = "$sourceId|$chapterUrl|$index"

    /** The chapter's own language (an English scanlation of a Japanese manga) beats the title's. */
    fun sourceLanguage(chapterLanguage: String?, mangaLanguage: String?): String? =
        chapterLanguage?.substringBefore('-') ?: mangaLanguage
}

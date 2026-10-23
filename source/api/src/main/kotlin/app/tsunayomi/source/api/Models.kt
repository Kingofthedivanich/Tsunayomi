package app.tsunayomi.source.api

import app.tsunayomi.source.api.SManga.Status

data class MangaPage(
    val items: List<SManga>,
    val hasNext: Boolean,
)

data class SManga(
    /** Key of the manga inside its source: a path, an API id or a file URI. */
    val url: String,
    val title: String,
    val coverUrl: String?,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: Status = Status.Unknown,
    /** Hint for the reader and the translator; null lets the user's default apply. */
    val readingDirection: ReadingDirection? = null,
    /** ISO 639-1 language of the original text, used to pick the OCR engine. */
    val originalLang: String? = null,
) {
    enum class Status { Unknown, Ongoing, Completed, Hiatus, Cancelled }
}

enum class ReadingDirection { LeftToRight, RightToLeft, Vertical, Webtoon }

data class SChapter(
    val url: String,
    val name: String,
    /** Chapter number, or a negative value if the source does not know it. */
    val number: Float,
    val uploadedAt: Long? = null,
    val scanlator: String? = null,
)

sealed interface SPage {
    val index: Int

    data class Remote(
        override val index: Int,
        val imageUrl: String,
        val headers: Map<String, String> = emptyMap(),
    ) : SPage

    /** A page on the device: file://, content://, or an entry inside an archive or PDF. */
    data class Local(
        override val index: Int,
        val uri: String,
    ) : SPage
}

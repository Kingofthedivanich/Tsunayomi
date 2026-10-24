package app.tsunayomi.core.model

import kotlinx.serialization.Serializable

data class Manga(
    val id: Long,
    val sourceId: Long,
    val url: String,
    val title: String,
    val coverUrl: String?,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.Unknown,
    val favorite: Boolean = false,
    val dateAdded: Long = 0,
    val readerMode: ReaderMode? = null,
    val originalLang: String? = null,
    val translatePages: Boolean? = null,
)

@Serializable
enum class MangaStatus {
    Unknown,
    Ongoing,
    Completed,
    Hiatus,
    Cancelled,
}

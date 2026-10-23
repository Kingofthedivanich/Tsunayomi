package app.tsunayomi.source.komga

import kotlinx.serialization.Serializable

@Serializable
internal data class PageDto<T>(val content: List<T>, val last: Boolean = true)

@Serializable
internal data class SeriesDto(
    val id: String,
    val name: String,
    val metadata: SeriesMetadataDto = SeriesMetadataDto(),
    val booksMetadata: BooksMetadataDto = BooksMetadataDto(),
)

@Serializable
internal data class SeriesMetadataDto(
    val status: String? = null,
    val title: String? = null,
    val summary: String = "",
    val readingDirection: String = "",
    val language: String = "",
    val genres: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
)

@Serializable
internal data class BooksMetadataDto(
    val authors: List<AuthorDto> = emptyList(),
    val summary: String = "",
)

@Serializable
internal data class AuthorDto(val name: String, val role: String)

@Serializable
internal data class BookDto(
    val id: String,
    val name: String,
    val number: Float = 0f,
    val fileLastModified: String? = null,
    val metadata: BookMetadataDto = BookMetadataDto(),
    val media: MediaDto = MediaDto(),
)

@Serializable
internal data class BookMetadataDto(
    val title: String = "",
    val number: String = "",
    val numberSort: Float = -1f,
    val releaseDate: String? = null,
)

@Serializable
internal data class MediaDto(val pagesCount: Int = 0)

@Serializable
internal data class BookPageDto(val number: Int, val fileName: String = "", val mediaType: String = "")

package app.tsunayomi.source.asura

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AsuraSeriesListResponse(
    val data: List<AsuraSeriesItemDto> = emptyList(),
    val meta: AsuraMetaDto? = null,
)

@Serializable
data class AsuraMetaDto(
    val total: Int = 0,
    @SerialName("per_page") val perPage: Int = 20,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class AsuraSeriesItemDto(
    val id: Long = 0,
    val slug: String = "",
    val title: String = "",
    @SerialName("alt_titles") val altTitles: List<String> = emptyList(),
    val description: String? = null,
    val cover: String? = null,
    val banner: String? = null,
    val status: String? = null,
    val type: String? = null,
    val author: String? = null,
    val artist: String? = null,
    @SerialName("popularity_rank") val popularityRank: Int? = null,
    val rating: Float? = null,
    @SerialName("chapter_count") val chapterCount: Int? = null,
    @SerialName("last_chapter_at") val lastChapterAt: String? = null,
    val genres: List<AsuraGenreDto> = emptyList(),
)

@Serializable
data class AsuraGenreDto(
    val id: Int = 0,
    val name: String = "",
    val slug: String = "",
)

@Serializable
data class AsuraSeriesDetailsResponse(
    val series: AsuraSeriesItemDto? = null,
)

@Serializable
data class AsuraChaptersResponse(
    val data: List<AsuraChapterItemDto> = emptyList(),
)

@Serializable
data class AsuraChapterItemDto(
    val id: Long = 0,
    @SerialName("series_id") val seriesId: Long = 0,
    val number: Float = 0f,
    val title: String? = null,
    val slug: String = "",
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("series_slug") val seriesSlug: String? = null,
)

@Serializable
data class AsuraChapterDetailsResponse(
    val data: AsuraChapterDetailsDataDto? = null,
)

@Serializable
data class AsuraChapterDetailsDataDto(
    val chapter: AsuraChapterWithPagesDto? = null,
)

@Serializable
data class AsuraChapterWithPagesDto(
    val id: Long = 0,
    val slug: String = "",
    val pages: List<AsuraPageDto> = emptyList(),
)

@Serializable
data class AsuraPageDto(
    val url: String = "",
    val width: Int? = null,
    val height: Int? = null,
)

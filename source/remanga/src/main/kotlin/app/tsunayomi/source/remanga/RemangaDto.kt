package app.tsunayomi.source.remanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class RemangaCatalogResponse(
    val content: List<RemangaCatalogItemDto> = emptyList(),
    val props: RemangaCatalogPropsDto? = null,
)

@Serializable
data class RemangaCatalogPropsDto(
    @SerialName("total_items") val totalItems: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
    val page: Int = 1,
)

@Serializable
data class RemangaCatalogItemDto(
    val id: Long = 0,
    val dir: String = "",
    @SerialName("rus_name") val rusName: String? = null,
    @SerialName("en_name") val enName: String? = null,
    @SerialName("main_name") val mainName: String? = null,
    @SerialName("secondary_name") val secondaryName: String? = null,
    @SerialName("issue_year") val issueYear: Int? = null,
    @SerialName("avg_rating") val avgRating: Float? = null,
    @SerialName("total_views") val totalViews: Long? = null,
    // A plain name in catalog results, {id, name} in title details; not used, so accept either.
    val type: JsonElement? = null,
    val cover: RemangaImageDto? = null,
    val img: RemangaImageDto? = null,
    val genres: List<RemangaGenreDto> = emptyList(),
    @SerialName("is_licensed") val isLicensed: Boolean = false,
)

@Serializable
data class RemangaImageDto(
    val low: String? = null,
    val mid: String? = null,
    val high: String? = null,
) {
    fun bestUrl(): String? = high ?: mid ?: low
}

@Serializable
data class RemangaGenreDto(
    val id: Int = 0,
    val name: String = "",
)

@Serializable
data class RemangaTitleResponse(
    val content: RemangaTitleDetailsDto? = null,
)

@Serializable
data class RemangaTitleDetailsDto(
    val id: Long = 0,
    val dir: String = "",
    @SerialName("rus_name") val rusName: String? = null,
    @SerialName("en_name") val enName: String? = null,
    @SerialName("main_name") val mainName: String? = null,
    @SerialName("secondary_name") val secondaryName: String? = null,
    @SerialName("another_name") val anotherName: String? = null,
    val description: String? = null,
    val cover: RemangaImageDto? = null,
    val img: RemangaImageDto? = null,
    val status: RemangaStatusDto? = null,
    // A plain name in catalog results, {id, name} in title details; not used, so accept either.
    val type: JsonElement? = null,
    val genres: List<RemangaGenreDto> = emptyList(),
    val branches: List<RemangaBranchDto> = emptyList(),
    @SerialName("is_licensed") val isLicensed: Boolean = false,
    /** People and companies by role: "1" writers, "2" artists or studios, "3" publishing platforms. */
    val creators: Map<String, List<RemangaCreatorDto>> = emptyMap(),
)

@Serializable
data class RemangaCreatorDto(
    val id: Long = 0,
    val name: String = "",
)

/** The v2 catalog, the only one that filters by creator. */
@Serializable
data class RemangaV2CatalogResponse(
    val next: String? = null,
    val results: List<RemangaCatalogItemDto> = emptyList(),
)

@Serializable
data class RemangaFormsResponse(val content: RemangaForms? = null)

@Serializable
data class RemangaForms(val genres: List<RemangaGenreDto> = emptyList())

@Serializable
data class RemangaStatusDto(
    val id: Int = 0,
    val name: String = "",
)

@Serializable
data class RemangaBranchDto(
    val id: Long = 0,
    @SerialName("count_chapters") val countChapters: Int = 0,
    val publishers: List<RemangaPublisherDto> = emptyList(),
)

@Serializable
data class RemangaPublisherDto(
    val id: Long = 0,
    val name: String = "",
    val dir: String? = null,
)

@Serializable
data class RemangaChaptersResponse(
    val content: List<RemangaChapterItemDto> = emptyList(),
)

@Serializable
data class RemangaChapterItemDto(
    val id: Long = 0,
    val tome: Int = 1,
    val chapter: String = "",
    val name: String? = null,
    @SerialName("upload_date") val uploadDate: String? = null,
    @SerialName("is_paid") val isPaid: Boolean = false,
    @SerialName("is_bought") val isBought: Boolean = false,
    val publishers: List<RemangaPublisherDto> = emptyList(),
)

@Serializable
data class RemangaChapterDetailsResponse(
    val content: RemangaChapterDetailsContent? = null,
    val pages: JsonElement? = null,
)

@Serializable
data class RemangaChapterDetailsContent(
    val id: Long = 0,
    val pages: JsonElement? = null,
    val server: RemangaServerDto? = null,
)

@Serializable
data class RemangaServerDto(
    val id: Int = 0,
    val name: String = "",
    val link: String = "",
    @SerialName("fallback_link") val fallbackLink: String? = null,
)

fun extractPageLinks(pagesElement: JsonElement?): List<String> {
    if (pagesElement == null) return emptyList()
    val links = mutableListOf<String>()
    fun walk(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                val link = element["link"]?.jsonPrimitive?.contentOrNull
                if (!link.isNullOrBlank()) {
                    links.add(link)
                }
            }
            is JsonArray -> {
                for (child in element) {
                    walk(child)
                }
            }
            else -> {}
        }
    }
    walk(pagesElement)
    return links
}

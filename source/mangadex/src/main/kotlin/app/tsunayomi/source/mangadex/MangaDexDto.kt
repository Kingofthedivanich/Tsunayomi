package app.tsunayomi.source.mangadex

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

/** MangaDex sends empty localized maps as `[]` instead of `{}`. */
internal object LocalizedStringSerializer : JsonTransformingSerializer<Map<String, String>>(
    MapSerializer(String.serializer(), String.serializer()),
) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonArray) JsonObject(emptyMap()) else element
}

internal typealias LocalizedString = @Serializable(with = LocalizedStringSerializer::class) Map<String, String>

@Serializable
internal data class MangaListResponse(
    val data: List<MangaDto>,
    val limit: Int,
    val offset: Int,
    val total: Int,
)

@Serializable
internal data class MangaResponse(val data: MangaDto)

@Serializable
internal data class MangaDto(
    val id: String,
    val attributes: MangaAttributes,
    val relationships: List<RelationshipDto> = emptyList(),
)

@Serializable
internal data class MangaAttributes(
    val title: LocalizedString = emptyMap(),
    val altTitles: List<LocalizedString> = emptyList(),
    val description: LocalizedString = emptyMap(),
    val status: String? = null,
    val originalLanguage: String? = null,
    val tags: List<TagDto> = emptyList(),
)

@Serializable
internal data class TagDto(val id: String, val attributes: TagAttributes)

@Serializable
internal data class TagListResponse(val data: List<TagDto>)

/** Authors and artists share this shape; the API's author search covers both. */
@Serializable
internal data class AuthorListResponse(val data: List<RelationshipDto>)

@Serializable
internal data class TagAttributes(
    val name: LocalizedString = emptyMap(),
    val group: String? = null,
)

@Serializable
internal data class RelationshipDto(
    val id: String,
    val type: String,
    val attributes: RelationshipAttributes? = null,
)

@Serializable
internal data class RelationshipAttributes(
    val fileName: String? = null,
    val name: String? = null,
)

@Serializable
internal data class ChapterListResponse(
    val data: List<ChapterDto>,
    val limit: Int,
    val offset: Int,
    val total: Int,
)

@Serializable
internal data class ChapterDto(
    val id: String,
    val attributes: ChapterAttributes,
    val relationships: List<RelationshipDto> = emptyList(),
)

@Serializable
internal data class ChapterAttributes(
    val volume: String? = null,
    val chapter: String? = null,
    val title: String? = null,
    val translatedLanguage: String? = null,
    val externalUrl: String? = null,
    val publishAt: String? = null,
    val pages: Int = 0,
)

@Serializable
internal data class AtHomeResponse(val baseUrl: String, val chapter: AtHomeChapter)

@Serializable
internal data class AtHomeChapter(
    val hash: String,
    val data: List<String>,
    val dataSaver: List<String> = emptyList(),
)


package app.tsunayomi.source.naver

import kotlinx.serialization.Serializable

// Responses of comic.naver.com/api, the JSON the site's own web app loads.

@Serializable
internal data class NaverWeekdayList(val titleListMap: Map<String, List<NaverTitle>> = emptyMap())

@Serializable
internal data class NaverTitle(
    val titleId: Long,
    val titleName: String,
    val author: String? = null,
    val displayAuthor: String? = null,
    val thumbnailUrl: String? = null,
    val synopsis: String? = null,
    val adult: Boolean = false,
    val finish: Boolean = false,
    val finished: Boolean = false,
    val webtoonLevelCode: String? = null,
)

@Serializable
internal data class NaverSearchAll(val searchWebtoonResult: NaverSearchResult? = null)

@Serializable
internal data class NaverSearchResult(val searchViewList: List<NaverTitle> = emptyList())

@Serializable
internal data class NaverTitleInfo(
    val titleId: Long,
    val titleName: String,
    val thumbnailUrl: String? = null,
    val synopsis: String? = null,
    val finished: Boolean = false,
    val communityArtists: List<NaverArtist> = emptyList(),
    val curationTagList: List<NaverTag> = emptyList(),
)

@Serializable
internal data class NaverArtist(val name: String, val artistTypeList: List<String> = emptyList())

@Serializable
internal data class NaverTag(val tagName: String, val curationType: String? = null)

@Serializable
internal data class NaverArticleList(
    val articleList: List<NaverArticle> = emptyList(),
    val pageInfo: NaverPageInfo? = null,
)

@Serializable
internal data class NaverArticle(
    val no: Int,
    val subtitle: String = "",
    val charge: Boolean = false,
    val serviceDateDescription: String? = null,
)

@Serializable
internal data class NaverPageInfo(val totalPages: Int = 1)

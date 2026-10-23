package app.tsunayomi.source.mangadex

import app.tsunayomi.source.api.Filter
import app.tsunayomi.source.api.FilterList
import java.util.Locale

/** Filter labels stay in English: they mirror MangaDex's own terms and are shown next to its content. */
internal object MangaDexFilters {
    val sortOptions = listOf(
        "Relevance" to "relevance",
        "Follows" to "followedCount",
        "Latest upload" to "latestUploadedChapter",
        "Rating" to "rating",
        "Title" to "title",
        "Year" to "year",
        "Recently added" to "createdAt",
    )
    val statusOptions = listOf("Any" to null, "Ongoing" to "ongoing", "Completed" to "completed", "Hiatus" to "hiatus", "Cancelled" to "cancelled")
    val originalLanguageOptions = listOf("Any" to null, "Japanese (manga)" to "ja", "Korean (manhwa)" to "ko", "Chinese (manhua)" to "zh", "English" to "en")
    val contentRatings = listOf("Safe" to "safe", "Suggestive" to "suggestive", "Erotica" to "erotica")

    /** Group name used to find the language filter again; its boxes follow [MangaDexSource.LANGUAGES]. */
    internal const val LANGUAGES_GROUP = "Chapter languages"

    fun create(languages: Set<String>) = FilterList(
        Filter.Group(
            LANGUAGES_GROUP,
            MangaDexSource.LANGUAGES.map { code -> Filter.CheckBox(languageName(code), state = code in languages) },
        ),
        Filter.Sort("Sort", sortOptions.map { it.first }, Filter.Sort.Selection(index = 1, ascending = false)),
        Filter.Select("Status", statusOptions.map { it.first }),
        Filter.Select("Original language", originalLanguageOptions.map { it.first }),
        Filter.Group(
            "Content rating",
            contentRatings.mapIndexed { index, (label, _) -> Filter.CheckBox(label, state = index < 2) },
        ),
    )
}

/** Chapter languages ticked in [filters], in the order of [MangaDexSource.LANGUAGES]. */
internal fun MangaDexFilters.selectedLanguages(filters: List<Filter<*>>): Set<String> {
    val group = filters.firstOrNull { it is Filter.Group<*> && it.name == LANGUAGES_GROUP } as? Filter.Group<*> ?: return emptySet()
    return group.state.mapIndexedNotNull { index, box ->
        MangaDexSource.LANGUAGES.getOrNull(index)?.takeIf { (box as? Filter.CheckBox)?.state == true }
    }.toCollection(LinkedHashSet())
}

/** "Русский", "English (Latin America)"… in the app language, so the filter reads naturally. */
private fun languageName(code: String): String {
    val tag = when (code) {
        "es-la" -> "es-419"
        else -> code
    }
    return Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

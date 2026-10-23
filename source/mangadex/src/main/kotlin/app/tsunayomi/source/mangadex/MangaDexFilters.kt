package app.tsunayomi.source.mangadex

import app.tsunayomi.source.api.Filter
import app.tsunayomi.source.api.FilterList

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

    fun create() = FilterList(
        Filter.Sort("Sort", sortOptions.map { it.first }, Filter.Sort.Selection(index = 1, ascending = false)),
        Filter.Select("Status", statusOptions.map { it.first }),
        Filter.Select("Original language", originalLanguageOptions.map { it.first }),
        Filter.Group(
            "Content rating",
            contentRatings.mapIndexed { index, (label, _) -> Filter.CheckBox(label, state = index < 2) },
        ),
    )
}

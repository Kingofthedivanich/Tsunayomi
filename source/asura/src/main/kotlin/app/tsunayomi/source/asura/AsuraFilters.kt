package app.tsunayomi.source.asura

import app.tsunayomi.source.api.Filter
import app.tsunayomi.source.api.FilterList

object AsuraFilters {
    val sortOptions = listOf(
        "Popular" to ("popular" to "desc"),
        "Latest update" to ("update" to "desc"),
        "Rating" to ("rating" to "desc"),
        "Title" to ("title" to "asc"),
    )

    val statusOptions = listOf(
        "All" to "all",
        "Ongoing" to "ongoing",
        "Completed" to "completed",
        "Hiatus" to "hiatus",
    )

    val typeOptions = listOf(
        "Comics / Manhwa" to "comic",
        "All" to "all",
        "Novels" to "novel",
    )

    fun create(): FilterList = FilterList(
        Filter.Select("Sort", sortOptions.map { it.first }, state = 0),
        Filter.Select("Status", statusOptions.map { it.first }, state = 0),
        Filter.Select("Type", typeOptions.map { it.first }, state = 0),
    )
}

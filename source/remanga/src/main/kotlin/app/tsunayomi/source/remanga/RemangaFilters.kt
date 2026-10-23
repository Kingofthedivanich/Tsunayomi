package app.tsunayomi.source.remanga

import app.tsunayomi.source.api.Filter
import app.tsunayomi.source.api.FilterList

object RemangaFilters {
    val sortOptions = listOf(
        "По рейтингу" to "-rating",
        "По популярности" to "-votes",
        "По дате обновления" to "-chapter_date",
        "По новизне" to "-id",
        "По просмотрам" to "-total_views",
    )

    val typeOptions = listOf(
        "Все" to "",
        "Манхва" to "Манхва",
        "Манга" to "Манга",
        "Маньхуа" to "Маньхуа",
        "Рукомикс" to "Рукомикс",
        "Комикс" to "Комикс",
    )

    val ageRatingOptions = listOf(
        "Любой" to "",
        "Для всех (0+)" to "0",
        "Подростковый (16+)" to "1",
        "Для взрослых (18+)" to "2",
    )

    fun create(): FilterList = FilterList(
        Filter.Select("Сортировка", sortOptions.map { it.first }, state = 0),
        Filter.Select("Тип произведения", typeOptions.map { it.first }, state = 0),
        Filter.Select("Возрастной рейтинг", ageRatingOptions.map { it.first }, state = 0),
    )
}

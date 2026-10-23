package app.tsunayomi.source.asura

import app.tsunayomi.source.api.Filter
import app.tsunayomi.source.api.FilterList

object AsuraFilters {
    val sortOptions = listOf(
        "По популярности" to ("popular" to "desc"),
        "По дате обновления" to ("update" to "desc"),
        "По рейтингу" to ("rating" to "desc"),
        "По названию" to ("title" to "asc"),
    )

    val statusOptions = listOf(
        "Все" to "all",
        "Онгоинг" to "ongoing",
        "Завершён" to "completed",
        "Хиатус" to "hiatus",
    )

    val typeOptions = listOf(
        "Комиксы / Манхва" to "comic",
        "Все" to "all",
        "Новеллы" to "novel",
    )

    fun create(): FilterList = FilterList(
        Filter.Select("Сортировка", sortOptions.map { it.first }, state = 0),
        Filter.Select("Статус", statusOptions.map { it.first }, state = 0),
        Filter.Select("Тип", typeOptions.map { it.first }, state = 0),
    )
}

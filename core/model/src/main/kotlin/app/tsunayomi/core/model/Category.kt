package app.tsunayomi.core.model

data class Category(
    val id: Long,
    val name: String,
    val order: Int,
)

object DefaultCategories {
    const val READING_RU = "Читаю"
    const val READING_EN = "Reading"
    const val COMPLETED_RU = "Прочитано"
    const val COMPLETED_EN = "Completed"
    const val PLAN_TO_READ_RU = "Буду читать"
    const val PLAN_TO_READ_EN = "Plan to read"
    const val DROPPED_RU = "Брошено"
    const val DROPPED_EN = "Dropped"

    fun isReading(name: String): Boolean =
        name.equals(READING_RU, ignoreCase = true) || name.equals(READING_EN, ignoreCase = true)

    fun isCompleted(name: String): Boolean =
        name.equals(COMPLETED_RU, ignoreCase = true) || name.equals(COMPLETED_EN, ignoreCase = true)

    fun isPlanToRead(name: String): Boolean =
        name.equals(PLAN_TO_READ_RU, ignoreCase = true) || name.equals(PLAN_TO_READ_EN, ignoreCase = true)

    fun isDropped(name: String): Boolean =
        name.equals(DROPPED_RU, ignoreCase = true) || name.equals(DROPPED_EN, ignoreCase = true)

    fun isStatusCategory(name: String): Boolean =
        isReading(name) || isCompleted(name) || isPlanToRead(name) || isDropped(name)
}

package app.tsunayomi.core.database

import androidx.room.TypeConverter

internal class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(SEPARATOR)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEPARATOR)

    private companion object {
        // Unit separator: never appears in genre names.
        const val SEPARATOR = "\u001F"
    }
}

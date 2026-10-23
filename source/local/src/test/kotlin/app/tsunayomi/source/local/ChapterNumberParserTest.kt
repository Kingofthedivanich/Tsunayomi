package app.tsunayomi.source.local

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ChapterNumberParserTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        delimiter = '|',
        value = [
            "Chapter 12|12",
            "Ch.5.5 - Extra|5.5",
            "Vol.2 Ch.15 - Title|15",
            "Глава 7|7",
            "第12話|12",
            "c012.cbz|12",
            "012.cbz|12",
            "Volume 3 - 045|45",
            "Том 1 Глава 3,5|3.5",
            "Oneshot|-1",
        ],
    )
    fun parses(name: String, expected: Float) {
        assertEquals(expected, ChapterNumberParser.parse(name))
    }
}

package app.tsunayomi.updates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NewChaptersTextTest {
    private val found = listOf(
        NewChapters(1, "Alpha", listOf("Ch. 10")),
        NewChapters(2, "Beta", listOf("Ch. 3", "Ch. 4", "Ch. 5")),
        NewChapters(3, "Gamma", listOf("Ch. 1")),
        NewChapters(4, "Delta", listOf("Ch. 7")),
    )

    @Test
    fun `one line per title with the newest chapter`() {
        assertEquals(listOf("Alpha — Ch. 10", "Beta — Ch. 5 (+2)"), NewChaptersText.lines(found, maxLines = 2))
    }

    @Test
    fun `titles are shortened with a count`() {
        assertEquals("Alpha, Beta, Gamma +1", NewChaptersText.titles(found))
        assertEquals("Alpha", NewChaptersText.titles(found.take(1)))
    }
}

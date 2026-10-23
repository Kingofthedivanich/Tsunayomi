package app.tsunayomi.data

import app.tsunayomi.core.model.Chapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReadingOrderTest {
    private fun chapter(id: Long, read: Boolean = false) =
        Chapter(id = id, mangaId = 1, url = "$id", name = "Ch $id", number = id.toFloat(), read = read, sourceOrder = id.toInt())

    @Test
    fun `continue picks the first unread chapter`() {
        val chapters = listOf(chapter(1, read = true), chapter(2), chapter(3))
        assertEquals(2L, ReadingOrder.continueChapter(chapters)?.id)
    }

    @Test
    fun `continue starts over when everything is read`() {
        val chapters = listOf(chapter(1, read = true), chapter(2, read = true))
        assertEquals(1L, ReadingOrder.continueChapter(chapters)?.id)
    }

    @Test
    fun `continue on empty list is null`() {
        assertNull(ReadingOrder.continueChapter(emptyList()))
    }

    @Test
    fun `resume keeps an unfinished chapter`() {
        val chapters = listOf(chapter(1, read = true), chapter(2), chapter(3))
        assertEquals(2L, ReadingOrder.resumeChapter(chapters, lastChapterId = 2)?.id)
    }

    @Test
    fun `resume moves past a finished chapter`() {
        val chapters = listOf(chapter(1, read = true), chapter(2, read = true), chapter(3))
        assertEquals(3L, ReadingOrder.resumeChapter(chapters, lastChapterId = 2)?.id)
    }

    @Test
    fun `resume stays on the last chapter at the end of the series`() {
        val chapters = listOf(chapter(1, read = true), chapter(2, read = true))
        assertEquals(2L, ReadingOrder.resumeChapter(chapters, lastChapterId = 2)?.id)
    }

    @Test
    fun `resume falls back to continue when the chapter disappeared`() {
        val chapters = listOf(chapter(1, read = true), chapter(2))
        assertEquals(2L, ReadingOrder.resumeChapter(chapters, lastChapterId = 99)?.id)
    }
}

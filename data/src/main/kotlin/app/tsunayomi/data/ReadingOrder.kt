package app.tsunayomi.data

import app.tsunayomi.core.model.Chapter

/** Pure rules about where reading continues; kept apart from repositories so they are easy to test. */
object ReadingOrder {
    /**
     * The chapter "Continue" opens: the first unread one in reading order,
     * or the first chapter when everything is read (start over).
     */
    fun continueChapter(chaptersInReadingOrder: List<Chapter>): Chapter? =
        chaptersInReadingOrder.firstOrNull { !it.read } ?: chaptersInReadingOrder.firstOrNull()

    /**
     * The chapter to resume from history: the same one if it is unfinished, otherwise the one after it,
     * falling back to the same chapter at the end of the series.
     */
    fun resumeChapter(chaptersInReadingOrder: List<Chapter>, lastChapterId: Long): Chapter? {
        val index = chaptersInReadingOrder.indexOfFirst { it.id == lastChapterId }
        if (index == -1) return continueChapter(chaptersInReadingOrder)
        val last = chaptersInReadingOrder[index]
        return if (last.read) chaptersInReadingOrder.getOrNull(index + 1) ?: last else last
    }
}

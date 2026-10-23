package app.tsunayomi.updates

/** What one library update found for one title. */
data class NewChapters(val mangaId: Long, val mangaTitle: String, val chapterNames: List<String>)

/** Notification text for a library update result; plain functions so the wording is testable. */
internal object NewChaptersText {
    /** Up to [maxLines] lines "Title — Chapter", one per title, newest chapter first. */
    fun lines(found: List<NewChapters>, maxLines: Int = 5): List<String> = found.take(maxLines).map { item ->
        val chapters = item.chapterNames
        val chapterText = if (chapters.size == 1) chapters.single() else "${chapters.last()} (+${chapters.size - 1})"
        "${item.mangaTitle} — $chapterText"
    }

    fun titles(found: List<NewChapters>, maxTitles: Int = 3): String {
        val shown = found.take(maxTitles).joinToString(", ") { it.mangaTitle }
        val rest = found.size - maxTitles
        return if (rest > 0) "$shown +$rest" else shown
    }
}

package app.tsunayomi.data

import app.tsunayomi.core.database.dao.ChapterDao
import app.tsunayomi.core.database.dao.HistoryDao
import app.tsunayomi.core.database.entity.HistoryEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressRepository @Inject constructor(
    private val chapterDao: ChapterDao,
    private val historyDao: HistoryDao,
) {
    /** Records the page the reader is on. [finished] marks the chapter read. */
    suspend fun saveProgress(chapterId: Long, page: Int, finished: Boolean) {
        val chapter = chapterDao.getById(chapterId) ?: return
        // Re-reading a finished chapter must not mark it unread again.
        chapterDao.updateProgress(chapterId, read = chapter.read || finished, lastPageRead = page)
        historyDao.upsert(HistoryEntity(chapterId, lastReadAt = System.currentTimeMillis(), timeRead = 0))
    }

    suspend fun setRead(chapterIds: List<Long>, read: Boolean) {
        chapterDao.setRead(chapterIds, read)
    }
}

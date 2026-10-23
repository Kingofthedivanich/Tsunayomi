package app.tsunayomi.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.tsunayomi.core.database.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download WHERE manga_id = :mangaId")
    fun observeForManga(mangaId: Long): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download WHERE state IN ('Queued', 'Downloading', 'Translating') ORDER BY created_at")
    fun observeActive(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download WHERE chapter_id = :chapterId")
    suspend fun get(chapterId: Long): DownloadEntity?

    @Query("SELECT * FROM download WHERE manga_id = :mangaId")
    suspend fun getForManga(mangaId: Long): List<DownloadEntity>

    @Query("SELECT * FROM download WHERE state = 'Queued' ORDER BY created_at LIMIT 1")
    suspend fun nextQueued(): DownloadEntity?

    /** Re-queues work interrupted by a killed process, so the worker resumes it. */
    @Query("UPDATE download SET state = 'Queued', pages_done = 0 WHERE state IN ('Downloading', 'Translating')")
    suspend fun requeueInterrupted()

    /** Adds chapters to the queue; chapters already downloaded or queued are left alone. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(downloads: List<DownloadEntity>)

    @Query("UPDATE download SET state = 'Queued', pages_done = 0, error = NULL WHERE chapter_id = :chapterId")
    suspend fun retry(chapterId: Long)

    @Query("UPDATE download SET state = :state, pages_done = :done, pages_total = :total WHERE chapter_id = :chapterId")
    suspend fun setProgress(chapterId: Long, state: String, done: Int, total: Int)

    @Query("UPDATE download SET state = 'Done', file_path = :path, pages_total = :total, pages_done = :total, error = :error WHERE chapter_id = :chapterId")
    suspend fun setDone(chapterId: Long, path: String, total: Int, error: String? = null)

    @Query("UPDATE download SET state = 'Failed', error = :error WHERE chapter_id = :chapterId")
    suspend fun setFailed(chapterId: Long, error: String)

    @Query("DELETE FROM download WHERE chapter_id IN (:chapterIds)")
    suspend fun delete(chapterIds: List<Long>)
}

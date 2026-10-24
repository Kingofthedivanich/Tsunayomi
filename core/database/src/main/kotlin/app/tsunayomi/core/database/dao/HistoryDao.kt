package app.tsunayomi.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.tsunayomi.core.database.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY last_read_at DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT h.* FROM history h INNER JOIN chapter c ON h.chapter_id = c.id WHERE c.manga_id = :mangaId")
    suspend fun getByManga(mangaId: Long): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE chapter_id = :chapterId")
    suspend fun getByChapterId(chapterId: Long): HistoryEntity?

    @Upsert
    suspend fun upsert(history: HistoryEntity)

    @Query("DELETE FROM history WHERE chapter_id = :chapterId")
    suspend fun delete(chapterId: Long)
}

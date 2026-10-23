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

    @Upsert
    suspend fun upsert(history: HistoryEntity)

    @Query("DELETE FROM history WHERE chapter_id = :chapterId")
    suspend fun delete(chapterId: Long)
}

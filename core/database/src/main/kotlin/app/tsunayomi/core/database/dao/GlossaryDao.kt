package app.tsunayomi.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.tsunayomi.core.database.entity.GlossaryTermEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GlossaryDao {
    @Query("SELECT * FROM glossary_term WHERE manga_id = :mangaId AND target_lang = :targetLang ORDER BY created_at")
    suspend fun get(mangaId: Long, targetLang: String): List<GlossaryTermEntity>

    @Query("SELECT * FROM glossary_term WHERE manga_id = :mangaId ORDER BY target_lang, source COLLATE NOCASE")
    fun observe(mangaId: Long): Flow<List<GlossaryTermEntity>>

    /** First translation wins: later batches must not rename a character mid-series. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(terms: List<GlossaryTermEntity>)

    @Query("DELETE FROM glossary_term WHERE manga_id = :mangaId AND target_lang = :targetLang AND source = :source")
    suspend fun delete(mangaId: Long, targetLang: String, source: String)

    @Query("DELETE FROM glossary_term WHERE manga_id = :mangaId")
    suspend fun clear(mangaId: Long)
}

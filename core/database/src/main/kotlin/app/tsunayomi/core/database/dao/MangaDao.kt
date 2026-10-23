package app.tsunayomi.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.tsunayomi.core.database.entity.MangaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    @Query("SELECT * FROM manga WHERE favorite = 1 ORDER BY title COLLATE NOCASE")
    fun observeFavorites(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE id = :id")
    fun observeById(id: Long): Flow<MangaEntity?>

    @Query("SELECT * FROM manga WHERE source_id = :sourceId AND url = :url")
    suspend fun getBySourceAndUrl(sourceId: Long, url: String): MangaEntity?

    @Upsert
    suspend fun upsert(manga: MangaEntity): Long

    @Query("UPDATE manga SET favorite = :favorite, date_added = :dateAdded WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, dateAdded: Long)
}

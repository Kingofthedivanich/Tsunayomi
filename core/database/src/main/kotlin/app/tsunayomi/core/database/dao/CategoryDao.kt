package app.tsunayomi.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.tsunayomi.core.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY sort_order")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsert(category: CategoryEntity): Long

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun delete(id: Long)
}

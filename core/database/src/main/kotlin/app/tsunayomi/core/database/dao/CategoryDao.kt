package app.tsunayomi.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.tsunayomi.core.database.entity.CategoryEntity
import app.tsunayomi.core.database.entity.MangaCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CategoryDao {
    @Query("SELECT * FROM category ORDER BY sort_order, id")
    abstract fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category ORDER BY sort_order, id")
    abstract suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM manga_category")
    abstract fun observeMangaCategories(): Flow<List<MangaCategoryEntity>>

    @Query("SELECT category_id FROM manga_category WHERE manga_id = :mangaId")
    abstract fun observeCategoryIds(mangaId: Long): Flow<List<Long>>

    @Query("SELECT category_id FROM manga_category WHERE manga_id = :mangaId")
    abstract suspend fun getCategoryIds(mangaId: Long): List<Long>

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM category")
    protected abstract suspend fun nextOrder(): Int

    @Insert
    protected abstract suspend fun insert(category: CategoryEntity): Long

    @Transaction
    open suspend fun create(name: String): Long = insert(CategoryEntity(name = name, order = nextOrder()))

    @Query("UPDATE category SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM category WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("UPDATE category SET sort_order = :order WHERE id = :id")
    protected abstract suspend fun setOrder(id: Long, order: Int)

    /** Persists [orderedIds] as the new display order. */
    @Transaction
    open suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setOrder(id, index) }
    }

    @Query("DELETE FROM manga_category WHERE manga_id = :mangaId")
    protected abstract suspend fun clearMangaCategories(mangaId: Long)

    @Insert
    protected abstract suspend fun insertMangaCategories(links: List<MangaCategoryEntity>)

    @Transaction
    open suspend fun setMangaCategories(mangaId: Long, categoryIds: Collection<Long>) {
        clearMangaCategories(mangaId)
        insertMangaCategories(categoryIds.map { MangaCategoryEntity(mangaId, it) })
    }
}

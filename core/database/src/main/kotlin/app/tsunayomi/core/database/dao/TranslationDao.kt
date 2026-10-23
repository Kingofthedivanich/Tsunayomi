package app.tsunayomi.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.tsunayomi.core.database.entity.TranslationPageEntity

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translation_page WHERE page_key = :pageKey AND target_lang = :targetLang AND engine_key = :engineKey")
    suspend fun get(pageKey: String, targetLang: String, engineKey: String): TranslationPageEntity?

    @Upsert
    suspend fun upsert(entity: TranslationPageEntity)

    @Query("DELETE FROM translation_page")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM translation_page")
    suspend fun count(): Int
}

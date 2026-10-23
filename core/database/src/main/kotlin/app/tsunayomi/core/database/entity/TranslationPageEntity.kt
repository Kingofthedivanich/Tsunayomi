package app.tsunayomi.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** A translated page. [engineKey] includes the prompt version, so prompt changes invalidate old results. */
@Entity(tableName = "translation_page", primaryKeys = ["page_key", "target_lang", "engine_key"])
data class TranslationPageEntity(
    @ColumnInfo(name = "page_key") val pageKey: String,
    @ColumnInfo(name = "target_lang") val targetLang: String,
    @ColumnInfo(name = "engine_key") val engineKey: String,
    @ColumnInfo(name = "regions_json") val regionsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

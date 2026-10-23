package app.tsunayomi.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** An established translation of a name or term within one manga, so it stays the same across pages and chapters. */
@Entity(
    tableName = "glossary_term",
    primaryKeys = ["manga_id", "target_lang", "source"],
    foreignKeys = [
        ForeignKey(entity = MangaEntity::class, parentColumns = ["id"], childColumns = ["manga_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("manga_id")],
)
data class GlossaryTermEntity(
    @ColumnInfo(name = "manga_id") val mangaId: Long,
    @ColumnInfo(name = "target_lang") val targetLang: String,
    val source: String,
    val translation: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

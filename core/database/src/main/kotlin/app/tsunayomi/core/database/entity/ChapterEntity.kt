package app.tsunayomi.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapter",
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["manga_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["manga_id", "url"], unique = true)],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "manga_id") val mangaId: Long,
    val url: String,
    val name: String,
    val number: Float,
    val scanlator: String?,
    @ColumnInfo(name = "uploaded_at") val uploadedAt: Long?,
    val read: Boolean,
    @ColumnInfo(name = "last_page_read") val lastPageRead: Int,
    val bookmark: Boolean,
    @ColumnInfo(name = "source_order") val sourceOrder: Int,
    /** Language of the text in this chapter (ISO 639-1), when the source knows it. */
    @ColumnInfo(name = "language") val language: String? = null,
    /**
     * When a library update (or a later refresh) found this chapter; 0 for chapters that came with the title's
     * first chapter list, so adding a title does not flood the Updates tab with its whole back catalogue.
     */
    @ColumnInfo(name = "fetched_at", defaultValue = "0") val fetchedAt: Long = 0,
)

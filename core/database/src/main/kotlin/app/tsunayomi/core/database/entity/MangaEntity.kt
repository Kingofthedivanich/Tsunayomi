package app.tsunayomi.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.tsunayomi.core.model.MangaStatus
import app.tsunayomi.core.model.ReaderMode

@Entity(
    tableName = "manga",
    indices = [Index(value = ["source_id", "url"], unique = true), Index("favorite")],
)
data class MangaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    val url: String,
    val title: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String?,
    val author: String?,
    val artist: String?,
    val description: String?,
    val genres: List<String>,
    val status: MangaStatus,
    val favorite: Boolean,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "reader_mode") val readerMode: ReaderMode?,
    @ColumnInfo(name = "original_lang") val originalLang: String?,
    /** When details were last fetched from the source; 0 means only the catalog entry is known. */
    @ColumnInfo(name = "details_updated_at") val detailsUpdatedAt: Long,
)

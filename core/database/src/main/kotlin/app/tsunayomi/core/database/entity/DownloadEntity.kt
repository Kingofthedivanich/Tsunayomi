package app.tsunayomi.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A chapter queued for, being, or already downloaded. The row is the queue: the worker picks the oldest queued one. */
@Entity(
    tableName = "download",
    foreignKeys = [
        ForeignKey(entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["chapter_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("manga_id"), Index("state")],
)
data class DownloadEntity(
    @PrimaryKey @ColumnInfo(name = "chapter_id") val chapterId: Long,
    @ColumnInfo(name = "manga_id") val mangaId: Long,
    /** Queued, Downloading, Translating, Done or Failed. */
    val state: String,
    @ColumnInfo(name = "pages_done") val pagesDone: Int = 0,
    @ColumnInfo(name = "pages_total") val pagesTotal: Int = 0,
    /** Also translate the pages once downloaded, so offline reading needs no OCR or network. */
    val translate: Boolean,
    val error: String? = null,
    @ColumnInfo(name = "file_path") val filePath: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

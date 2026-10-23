package app.tsunayomi.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.tsunayomi.core.database.dao.CategoryDao
import app.tsunayomi.core.database.dao.ChapterDao
import app.tsunayomi.core.database.dao.HistoryDao
import app.tsunayomi.core.database.dao.LibraryDao
import app.tsunayomi.core.database.dao.MangaDao
import app.tsunayomi.core.database.entity.CategoryEntity
import app.tsunayomi.core.database.entity.ChapterEntity
import app.tsunayomi.core.database.entity.HistoryEntity
import app.tsunayomi.core.database.entity.MangaCategoryEntity
import app.tsunayomi.core.database.entity.MangaEntity

@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
        CategoryEntity::class,
        MangaCategoryEntity::class,
        HistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TsunayomiDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun categoryDao(): CategoryDao
    abstract fun historyDao(): HistoryDao
    abstract fun libraryDao(): LibraryDao
}

package app.tsunayomi.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.tsunayomi.core.database.dao.CategoryDao
import app.tsunayomi.core.database.dao.ChapterDao
import app.tsunayomi.core.database.dao.DownloadDao
import app.tsunayomi.core.database.dao.GlossaryDao
import app.tsunayomi.core.database.dao.HistoryDao
import app.tsunayomi.core.database.dao.LibraryDao
import app.tsunayomi.core.database.dao.MangaDao
import app.tsunayomi.core.database.dao.TranslationDao
import app.tsunayomi.core.database.entity.CategoryEntity
import app.tsunayomi.core.database.entity.ChapterEntity
import app.tsunayomi.core.database.entity.DownloadEntity
import app.tsunayomi.core.database.entity.GlossaryTermEntity
import app.tsunayomi.core.database.entity.HistoryEntity
import app.tsunayomi.core.database.entity.MangaCategoryEntity
import app.tsunayomi.core.database.entity.MangaEntity
import app.tsunayomi.core.database.entity.TranslationPageEntity

@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
        CategoryEntity::class,
        MangaCategoryEntity::class,
        HistoryEntity::class,
        TranslationPageEntity::class,
        GlossaryTermEntity::class,
        DownloadEntity::class,
    ],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
    ],
)
@TypeConverters(Converters::class)
abstract class TsunayomiDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun categoryDao(): CategoryDao
    abstract fun historyDao(): HistoryDao
    abstract fun libraryDao(): LibraryDao
    abstract fun translationDao(): TranslationDao
    abstract fun glossaryDao(): GlossaryDao
    abstract fun downloadDao(): DownloadDao
}

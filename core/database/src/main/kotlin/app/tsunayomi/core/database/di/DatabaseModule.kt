package app.tsunayomi.core.database.di

import android.content.Context
import androidx.room.Room
import app.tsunayomi.core.database.TsunayomiDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): TsunayomiDatabase =
        Room.databaseBuilder(context, TsunayomiDatabase::class.java, "tsunayomi.db").build()

    @Provides fun providesMangaDao(db: TsunayomiDatabase) = db.mangaDao()
    @Provides fun providesChapterDao(db: TsunayomiDatabase) = db.chapterDao()
    @Provides fun providesCategoryDao(db: TsunayomiDatabase) = db.categoryDao()
    @Provides fun providesHistoryDao(db: TsunayomiDatabase) = db.historyDao()
    @Provides fun providesLibraryDao(db: TsunayomiDatabase) = db.libraryDao()
    @Provides fun providesTranslationDao(db: TsunayomiDatabase) = db.translationDao()
}

package app.tsunayomi.translation.mangaocr

import app.tsunayomi.translation.api.JapaneseOcrProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MangaOcrModule {
    @Binds
    abstract fun bindsJapaneseOcrProvider(recognizer: MangaOcrRecognizer): JapaneseOcrProvider
}

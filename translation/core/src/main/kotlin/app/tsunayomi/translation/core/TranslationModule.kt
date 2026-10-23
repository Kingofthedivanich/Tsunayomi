package app.tsunayomi.translation.core

import app.tsunayomi.core.datastore.UserPreferencesDataSource
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.translation.api.PageTranslator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.Locale

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TranslationModule {
    @Binds
    abstract fun bindsPageTranslator(translator: DefaultPageTranslator): PageTranslator

    @Binds
    abstract fun bindsCache(cache: RoomTranslationCache): TranslationCache

    companion object {
        @Provides
        fun providesTarget(preferences: UserPreferencesDataSource): TranslationTarget = TranslationTarget {
            val settings = preferences.settings.first()
            val engine = settings.translationEngine.takeIf { it.implemented } ?: TranslationEngine.MlKit
            // Empty target follows the app language (per-app locale is reflected in the default locale).
            engine to settings.translationTargetLang.ifEmpty { Locale.getDefault().language }
        }
    }
}

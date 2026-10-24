package app.tsunayomi.translation.deepl

import app.tsunayomi.core.datastore.ServerPreferencesDataSource
import app.tsunayomi.translation.api.Translator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DeepLModule {
    @Provides
    @Singleton
    fun providesDeepLTranslatorInstance(client: OkHttpClient, preferences: ServerPreferencesDataSource): DeepLTranslator =
        DeepLTranslator(client.newBuilder().readTimeout(30, TimeUnit.SECONDS).build(), preferences.deepL)

    @Provides
    @Singleton
    @IntoSet
    fun providesDeepLTranslator(translator: DeepLTranslator): Translator = translator
}

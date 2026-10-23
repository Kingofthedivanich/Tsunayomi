package app.tsunayomi.translation.gemini

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
internal object GeminiModule {
    @Provides
    @Singleton
    @IntoSet
    fun providesGeminiTranslator(client: OkHttpClient, preferences: ServerPreferencesDataSource): Translator =
        // LLM answers can take a while for a dense page.
        GeminiTranslator(client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build(), preferences.gemini)
}

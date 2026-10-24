package app.tsunayomi.translation.openai

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
internal object OpenAiModule {
    @Provides
    @Singleton
    @IntoSet
    fun providesOpenAiTranslator(client: OkHttpClient, preferences: ServerPreferencesDataSource): Translator =
        OpenAiTranslator(client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build(), preferences.openAi)
}

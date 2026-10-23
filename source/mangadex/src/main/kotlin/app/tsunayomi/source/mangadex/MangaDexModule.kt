package app.tsunayomi.source.mangadex

import app.tsunayomi.core.network.SharedInterceptor
import app.tsunayomi.source.api.Source
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoSet
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object MangaDexModule {
    @Provides
    @Singleton
    @ElementsIntoSet
    fun providesSources(client: OkHttpClient): Set<Source> =
        MangaDexSource.LANGUAGES.mapTo(LinkedHashSet()) { lang -> MangaDexSource(lang, client) }

    @Provides
    @IntoSet
    @SharedInterceptor
    fun providesAtHomeReporter(client: Lazy<OkHttpClient>): Interceptor =
        MangaDexAtHomeReporter(reportClient = { client.get() })
}

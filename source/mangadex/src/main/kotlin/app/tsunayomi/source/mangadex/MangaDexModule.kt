package app.tsunayomi.source.mangadex

import app.tsunayomi.core.common.ApplicationScope
import app.tsunayomi.core.datastore.SourcePreferencesDataSource
import app.tsunayomi.core.network.SharedInterceptor
import app.tsunayomi.source.api.Source
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object MangaDexModule {
    @Provides
    @Singleton
    @IntoSet
    fun providesSource(
        client: OkHttpClient,
        preferences: SourcePreferencesDataSource,
        @ApplicationScope scope: CoroutineScope,
    ): Source {
        // The chosen languages, kept current so the source can read them synchronously (e.g. to build filters).
        val languages = preferences.mangaDexLanguages
            .map { it ?: MangaDexSource.defaultLanguages() }
            .stateIn(scope, SharingStarted.Eagerly, MangaDexSource.defaultLanguages())
        return MangaDexSource(
            client = client,
            languages = { languages.value },
            saveLanguages = preferences::setMangaDexLanguages,
        )
    }

    @Provides
    @IntoSet
    @SharedInterceptor
    fun providesAtHomeReporter(client: Lazy<OkHttpClient>): Interceptor =
        MangaDexAtHomeReporter(reportClient = { client.get() })
}

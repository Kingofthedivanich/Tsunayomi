package app.tsunayomi.source.asura

import app.tsunayomi.core.network.SharedInterceptor
import app.tsunayomi.source.api.Source
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AsuraModule {
    @Provides
    @Singleton
    @IntoSet
    fun providesAsuraSource(client: OkHttpClient): Source = AsuraSource(client)

    @Provides
    @IntoSet
    @SharedInterceptor
    fun providesAsuraInterceptor(): Interceptor = AsuraInterceptor()
}

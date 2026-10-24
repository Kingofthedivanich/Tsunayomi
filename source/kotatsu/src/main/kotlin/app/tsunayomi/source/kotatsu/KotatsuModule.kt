package app.tsunayomi.source.kotatsu

import app.tsunayomi.core.network.SharedInterceptor
import app.tsunayomi.source.api.DynamicSources
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor

@Module
@InstallIn(SingletonComponent::class)
internal abstract class KotatsuModule {
    @Binds
    @IntoSet
    abstract fun bindKotatsuDynamicSources(manager: KotatsuSourceManager): DynamicSources

    @Binds
    @IntoSet
    @SharedInterceptor
    abstract fun bindKotatsuImageInterceptor(interceptor: KotatsuImageInterceptor): Interceptor
}

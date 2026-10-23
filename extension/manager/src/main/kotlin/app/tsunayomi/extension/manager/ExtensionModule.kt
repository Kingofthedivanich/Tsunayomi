package app.tsunayomi.extension.manager

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
internal abstract class ExtensionModule {
    @Binds
    @IntoSet
    abstract fun bindsDynamicSources(manager: ExtensionManager): DynamicSources

    @Binds
    @IntoSet
    @SharedInterceptor
    abstract fun bindsImageInterceptor(interceptor: ExtensionImageInterceptor): Interceptor
}

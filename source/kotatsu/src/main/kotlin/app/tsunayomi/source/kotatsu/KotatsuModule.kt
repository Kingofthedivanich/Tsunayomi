package app.tsunayomi.source.kotatsu

import app.tsunayomi.source.api.DynamicSources
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class KotatsuModule {
    @Binds
    @IntoSet
    abstract fun bindKotatsuDynamicSources(manager: KotatsuSourceManager): DynamicSources
}

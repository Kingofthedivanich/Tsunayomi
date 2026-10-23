package app.tsunayomi.source.local

import app.tsunayomi.source.api.Source
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LocalSourceModule {
    @Binds
    @IntoSet
    abstract fun bindsLocalSource(source: LocalSource): Source
}

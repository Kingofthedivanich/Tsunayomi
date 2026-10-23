package app.tsunayomi.source.komga

import app.tsunayomi.source.api.Source
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal object KomgaModule {
    @Provides
    @IntoSet
    fun providesKomgaSource(holder: KomgaSourceHolder): Source = holder.source
}

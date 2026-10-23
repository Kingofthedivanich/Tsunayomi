package app.tsunayomi.data

import app.tsunayomi.source.api.CatalogSource
import app.tsunayomi.source.api.Source
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton

/** All sources known to the app. Each source module contributes itself with `@IntoSet`. */
@Singleton
class SourceManager @Inject constructor(
    sources: Set<@JvmSuppressWildcards Source>,
) {
    private val byId: Map<Long, Source> = sources.associateBy { it.id }

    val catalogSources: List<CatalogSource> = sources.filterIsInstance<CatalogSource>().sortedBy { it.id }

    fun get(id: Long): Source? = byId[id]

    fun getCatalog(id: Long): CatalogSource =
        byId[id] as? CatalogSource ?: throw SourceNotFoundException(id)
}

class SourceNotFoundException(val sourceId: Long) : Exception("Source $sourceId is not installed")

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SourceSetModule {
    /** Lets the app start even before any source module is on the classpath. */
    @Multibinds
    abstract fun sources(): Set<Source>
}

package app.tsunayomi.data

import app.tsunayomi.source.api.CatalogSource
import app.tsunayomi.source.api.DynamicSources
import app.tsunayomi.source.api.Source
import app.tsunayomi.source.api.SourceIds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All sources known to the app: the ones built in (each module contributes itself with `@IntoSet`)
 * and the ones that come and go at runtime, such as installed extensions.
 */
@Singleton
class SourceManager @Inject constructor(
    sources: Set<@JvmSuppressWildcards Source>,
    private val dynamic: Set<@JvmSuppressWildcards DynamicSources>,
) {
    private val builtIn: List<Source> = sources.toList()
    private val builtInById: Map<Long, Source> = builtIn.associateBy { it.id } +
        builtIn.flatMap { source -> source.legacyIds.map { it to source } }

    /** Local files first, then built-in sources, then the rest by name and language. */
    val catalogSources: Flow<List<CatalogSource>> =
        (if (dynamic.isEmpty()) flowOf(emptyList()) else combine(dynamic.map { it.browsable }) { it.toList().flatten() })
            .let { extra -> combine(flowOf(builtIn), extra) { a, b -> sorted(a + b) } }

    /** The current list, for one-off work such as a global search. */
    fun currentCatalogSources(): List<CatalogSource> = sorted(builtIn + dynamic.flatMap { it.awaitLoaded(); it.browsable.value })

    fun get(id: Long): Source? = builtInById[id] ?: dynamicNow().firstOrNull { it.id == id }

    fun getCatalog(id: Long): CatalogSource =
        get(id) as? CatalogSource ?: throw SourceNotFoundException(id)

    private fun dynamicNow(): List<Source> = dynamic.flatMap {
        it.awaitLoaded()
        it.sources.value
    }

    // An extension can reuse a built-in source's id (MangaDex extension sources have the ids our old
    // per-language MangaDex sources had); the built-in one wins, so the duplicate isn't offered.
    private fun sorted(sources: List<Source>): List<CatalogSource> = sources.filterIsInstance<CatalogSource>()
        .filter { it in builtIn || it.id !in builtInById }
        .sortedWith(
            compareBy(
                { it.id != SourceIds.LOCAL },
                { it !in builtIn },
                { it.name.lowercase() },
                { it.lang },
            ),
        )
}

class SourceNotFoundException(val sourceId: Long) : Exception("Source $sourceId is not installed")

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SourceSetModule {
    /** Lets the app start even before any source module is on the classpath. */
    @Multibinds
    abstract fun sources(): Set<Source>

    @Multibinds
    abstract fun dynamicSources(): Set<DynamicSources>
}

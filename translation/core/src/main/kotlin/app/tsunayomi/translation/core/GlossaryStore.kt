package app.tsunayomi.translation.core

import app.tsunayomi.core.database.dao.GlossaryDao
import app.tsunayomi.core.database.entity.GlossaryTermEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class GlossaryEntry(val targetLang: String, val source: String, val translation: String)

/** Per-manga translations of names and terms, collected from the translator's answers. */
interface GlossaryStore {
    suspend fun terms(mangaId: Long, targetLang: String): Map<String, String>
    suspend fun add(mangaId: Long, targetLang: String, terms: Map<String, String>)
    fun observe(mangaId: Long): Flow<List<GlossaryEntry>>
    suspend fun delete(mangaId: Long, entry: GlossaryEntry)
    suspend fun clear(mangaId: Long)
}

@Singleton
internal class RoomGlossaryStore @Inject constructor(private val dao: GlossaryDao) : GlossaryStore {
    override suspend fun terms(mangaId: Long, targetLang: String): Map<String, String> =
        dao.get(mangaId, targetLang).associate { it.source to it.translation }

    override suspend fun add(mangaId: Long, targetLang: String, terms: Map<String, String>) {
        if (terms.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.insertIfAbsent(terms.map { (source, translation) -> GlossaryTermEntity(mangaId, targetLang, source, translation, now) })
    }

    override fun observe(mangaId: Long): Flow<List<GlossaryEntry>> =
        dao.observe(mangaId).map { list -> list.map { GlossaryEntry(it.targetLang, it.source, it.translation) } }

    override suspend fun delete(mangaId: Long, entry: GlossaryEntry) = dao.delete(mangaId, entry.targetLang, entry.source)

    override suspend fun clear(mangaId: Long) = dao.clear(mangaId)
}

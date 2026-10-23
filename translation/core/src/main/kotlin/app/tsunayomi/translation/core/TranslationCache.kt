package app.tsunayomi.translation.core

import app.tsunayomi.core.database.dao.TranslationDao
import app.tsunayomi.core.database.entity.TranslationPageEntity
import app.tsunayomi.translation.api.TranslatedRegion
import kotlinx.serialization.json.Json
import javax.inject.Inject

interface TranslationCache {
    suspend fun get(pageKey: String, targetLang: String, engineKey: String): List<TranslatedRegion>?
    suspend fun put(pageKey: String, targetLang: String, engineKey: String, regions: List<TranslatedRegion>)
}

internal class RoomTranslationCache @Inject constructor(private val dao: TranslationDao) : TranslationCache {
    override suspend fun get(pageKey: String, targetLang: String, engineKey: String): List<TranslatedRegion>? =
        dao.get(pageKey, targetLang, engineKey)?.let { entity ->
            runCatching { json.decodeFromString<List<TranslatedRegion>>(entity.regionsJson) }.getOrNull()
        }

    override suspend fun put(pageKey: String, targetLang: String, engineKey: String, regions: List<TranslatedRegion>) {
        dao.upsert(
            TranslationPageEntity(
                pageKey = pageKey,
                targetLang = targetLang,
                engineKey = engineKey,
                regionsJson = json.encodeToString(regions),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

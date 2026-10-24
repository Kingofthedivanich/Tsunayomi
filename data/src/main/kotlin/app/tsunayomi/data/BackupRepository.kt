package app.tsunayomi.data

import app.tsunayomi.core.database.dao.CategoryDao
import app.tsunayomi.core.database.dao.ChapterDao
import app.tsunayomi.core.database.dao.GlossaryDao
import app.tsunayomi.core.database.dao.HistoryDao
import app.tsunayomi.core.database.dao.MangaDao
import app.tsunayomi.core.database.entity.ChapterEntity
import app.tsunayomi.core.database.entity.GlossaryTermEntity
import app.tsunayomi.core.database.entity.HistoryEntity
import app.tsunayomi.core.database.entity.MangaEntity
import app.tsunayomi.core.model.BackupCategory
import app.tsunayomi.core.model.BackupChapter
import app.tsunayomi.core.model.BackupGlossaryTerm
import app.tsunayomi.core.model.BackupHistory
import app.tsunayomi.core.model.BackupManga
import app.tsunayomi.core.model.BackupResult
import app.tsunayomi.core.model.BackupRoot
import app.tsunayomi.core.model.MangaStatus
import app.tsunayomi.core.model.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val mangaDao: MangaDao,
    private val categoryDao: CategoryDao,
    private val chapterDao: ChapterDao,
    private val historyDao: HistoryDao,
    private val glossaryDao: GlossaryDao,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun createBackup(outputStream: OutputStream, appVersion: String = ""): BackupResult = withContext(Dispatchers.IO) {
        try {
            val categories = categoryDao.getAll()
            val categoryMap = categories.associate { it.id to it.name }
            val favorites = mangaDao.getFavorites()

            val backupCategories = categories.map { BackupCategory(name = it.name, order = it.order) }

            var totalChapters = 0
            val backupMangaList = favorites.map { manga ->
                val categoryIds = categoryDao.getCategoryIds(manga.id)
                val categoryNames = categoryIds.mapNotNull { categoryMap[it] }

                val chapters = chapterDao.getByManga(manga.id)
                totalChapters += chapters.size

                val historyList = historyDao.getByManga(manga.id)
                val chapterMap = chapters.associate { it.id to it.url }
                val backupHistory = historyList.mapNotNull { hist ->
                    val chapterUrl = chapterMap[hist.chapterId] ?: return@mapNotNull null
                    BackupHistory(chapterUrl = chapterUrl, lastReadAt = hist.lastReadAt, timeRead = hist.timeRead)
                }

                val glossaryList = glossaryDao.getAllForManga(manga.id)
                val backupGlossary = glossaryList.map {
                    BackupGlossaryTerm(targetLang = it.targetLang, source = it.source, translation = it.translation)
                }

                BackupManga(
                    sourceId = manga.sourceId,
                    url = manga.url,
                    title = manga.title,
                    coverUrl = manga.coverUrl,
                    author = manga.author,
                    artist = manga.artist,
                    description = manga.description,
                    genres = manga.genres,
                    status = manga.status,
                    favorite = manga.favorite,
                    dateAdded = manga.dateAdded,
                    readerMode = manga.readerMode,
                    originalLang = manga.originalLang,
                    detailsUpdatedAt = manga.detailsUpdatedAt,
                    categories = categoryNames,
                    chapters = chapters.map {
                        BackupChapter(
                            url = it.url,
                            name = it.name,
                            number = it.number,
                            scanlator = it.scanlator,
                            uploadedAt = it.uploadedAt,
                            read = it.read,
                            lastPageRead = it.lastPageRead,
                            bookmark = it.bookmark,
                            sourceOrder = it.sourceOrder,
                            language = it.language,
                        )
                    },
                    history = backupHistory,
                    glossary = backupGlossary,
                )
            }

            val root = BackupRoot(
                version = BackupRoot.CURRENT_VERSION,
                createdAt = System.currentTimeMillis(),
                appVersion = appVersion,
                categories = backupCategories,
                manga = backupMangaList,
            )

            GZIPOutputStream(outputStream).bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(json.encodeToString(BackupRoot.serializer(), root))
                writer.flush()
            }

            BackupResult.Success(
                mangaCount = backupMangaList.size,
                categoryCount = backupCategories.size,
                chapterCount = totalChapters,
            )
        } catch (t: Throwable) {
            BackupResult.Error(t)
        }
    }

    fun restoreBackup(inputStream: InputStream): Flow<RestoreStep> = flow {
        val root = try {
            val raw = decompressIfNeeded(inputStream).bufferedReader(Charsets.UTF_8).use { it.readText() }
            json.decodeFromString(BackupRoot.serializer(), raw)
        } catch (t: Throwable) {
            emit(RestoreStep.Finished(RestoreResult.Error(t)))
            return@flow
        }

        var restoredMangaCount = 0
        var restoredChaptersCount = 0
        var restoredCategoriesCount = 0

        try {
            // 1. Categories
            val existingCategories = categoryDao.getAll().toMutableList()
            val categoryNameToId = existingCategories.associate { it.name to it.id }.toMutableMap()

            for (cat in root.categories) {
                if (cat.name !in categoryNameToId) {
                    val id = categoryDao.create(cat.name)
                    categoryNameToId[cat.name] = id
                    restoredCategoriesCount++
                }
            }

            // 2. Manga, Chapters, History, Glossary
            val totalManga = root.manga.size
            for ((index, bManga) in root.manga.withIndex()) {
                emit(RestoreStep.Progress(current = index + 1, total = totalManga, currentTitle = bManga.title))

                // Find or insert manga
                val existing = mangaDao.getBySourceAndUrl(bManga.sourceId, bManga.url)
                val mangaId = if (existing != null) {
                    val updated = existing.copy(
                        title = bManga.title.ifBlank { existing.title },
                        coverUrl = bManga.coverUrl ?: existing.coverUrl,
                        author = bManga.author ?: existing.author,
                        artist = bManga.artist ?: existing.artist,
                        description = bManga.description ?: existing.description,
                        genres = if (bManga.genres.isNotEmpty()) bManga.genres else existing.genres,
                        status = if (bManga.status != MangaStatus.Unknown) bManga.status else existing.status,
                        favorite = existing.favorite || bManga.favorite,
                        dateAdded = if (existing.dateAdded > 0) existing.dateAdded else bManga.dateAdded,
                        readerMode = bManga.readerMode ?: existing.readerMode,
                        originalLang = bManga.originalLang ?: existing.originalLang,
                    )
                    mangaDao.update(updated)
                    existing.id
                } else {
                    mangaDao.insert(
                        MangaEntity(
                            sourceId = bManga.sourceId,
                            url = bManga.url,
                            title = bManga.title,
                            coverUrl = bManga.coverUrl,
                            author = bManga.author,
                            artist = bManga.artist,
                            description = bManga.description,
                            genres = bManga.genres,
                            status = bManga.status,
                            favorite = bManga.favorite,
                            dateAdded = if (bManga.dateAdded > 0) bManga.dateAdded else System.currentTimeMillis(),
                            readerMode = bManga.readerMode,
                            originalLang = bManga.originalLang,
                            detailsUpdatedAt = bManga.detailsUpdatedAt,
                        ),
                    )
                }
                restoredMangaCount++

                // Categories for manga
                if (bManga.categories.isNotEmpty()) {
                    val currentCategoryIds = categoryDao.getCategoryIds(mangaId).toMutableSet()
                    for (catName in bManga.categories) {
                        val catId = categoryNameToId[catName]
                        if (catId != null) currentCategoryIds.add(catId)
                    }
                    categoryDao.setMangaCategories(mangaId, currentCategoryIds)
                }

                // Chapters
                if (bManga.chapters.isNotEmpty()) {
                    val incomingChapters = bManga.chapters.map { bc ->
                        ChapterEntity(
                            id = 0,
                            mangaId = mangaId,
                            url = bc.url,
                            name = bc.name,
                            number = bc.number,
                            scanlator = bc.scanlator,
                            uploadedAt = bc.uploadedAt,
                            read = bc.read,
                            lastPageRead = bc.lastPageRead,
                            bookmark = bc.bookmark,
                            sourceOrder = bc.sourceOrder,
                            language = bc.language,
                        )
                    }
                    chapterDao.restoreChapters(mangaId, incomingChapters)
                    restoredChaptersCount += bManga.chapters.size
                }

                // History
                if (bManga.history.isNotEmpty()) {
                    val chapters = chapterDao.getByManga(mangaId).associateBy { it.url }
                    for (h in bManga.history) {
                        val chapter = chapters[h.chapterUrl] ?: continue
                        val existingHistory = historyDao.getByChapterId(chapter.id)
                        if (existingHistory == null || h.lastReadAt > existingHistory.lastReadAt) {
                            historyDao.upsert(
                                HistoryEntity(
                                    chapterId = chapter.id,
                                    lastReadAt = h.lastReadAt,
                                    timeRead = h.timeRead,
                                ),
                            )
                        }
                    }
                }

                // Glossary
                if (bManga.glossary.isNotEmpty()) {
                    val terms = bManga.glossary.map {
                        GlossaryTermEntity(
                            mangaId = mangaId,
                            targetLang = it.targetLang,
                            source = it.source,
                            translation = it.translation,
                            createdAt = System.currentTimeMillis(),
                        )
                    }
                    glossaryDao.insertIfAbsent(terms)
                }
            }

            emit(
                RestoreStep.Finished(
                    RestoreResult.Success(
                        mangaCount = restoredMangaCount,
                        categoryCount = restoredCategoriesCount,
                        chapterCount = restoredChaptersCount,
                    ),
                ),
            )
        } catch (t: Throwable) {
            emit(RestoreStep.Finished(RestoreResult.Error(t)))
        }
    }.flowOn(Dispatchers.IO)

    private fun decompressIfNeeded(input: InputStream): InputStream {
        val pb = PushbackInputStream(input, 2)
        val b1 = pb.read()
        val b2 = pb.read()
        if (b1 != -1 && b2 != -1) {
            pb.unread(byteArrayOf(b1.toByte(), b2.toByte()))
            if (b1 == 0x1f && b2 == 0x8b) {
                return GZIPInputStream(pb)
            }
        }
        return pb
    }
}

sealed interface RestoreStep {
    data class Progress(val current: Int, val total: Int, val currentTitle: String) : RestoreStep
    data class Finished(val result: RestoreResult) : RestoreStep
}

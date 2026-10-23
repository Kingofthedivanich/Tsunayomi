package app.tsunayomi.data

import app.tsunayomi.core.common.Dispatcher
import app.tsunayomi.core.common.TsunayomiDispatcher
import app.tsunayomi.core.database.dao.ChapterDao
import app.tsunayomi.core.database.dao.MangaDao
import app.tsunayomi.core.database.entity.MangaEntity
import app.tsunayomi.core.model.Chapter
import app.tsunayomi.core.model.Manga
import app.tsunayomi.core.model.ReaderMode
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaRepository @Inject constructor(
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val sourceManager: SourceManager,
    @Dispatcher(TsunayomiDispatcher.IO) private val io: CoroutineDispatcher,
) {
    fun observeFavorites(): Flow<List<Manga>> =
        mangaDao.observeFavorites().map { list -> list.map { it.toModel() } }

    fun observeManga(id: Long): Flow<Manga?> = mangaDao.observeById(id).map { it?.toModel() }

    suspend fun getManga(id: Long): Manga? = mangaDao.getById(id)?.toModel()

    fun observeChapters(mangaId: Long): Flow<List<Chapter>> =
        chapterDao.observeByManga(mangaId).map { list -> list.map { it.toModel() } }

    suspend fun getChapters(mangaId: Long): List<Chapter> = chapterDao.getByManga(mangaId).map { it.toModel() }

    /** Returns the local id for a catalog entry, inserting it on first sight. Existing details are kept. */
    suspend fun getOrCreate(sourceId: Long, manga: SManga): Long = withContext(io) {
        val existing = mangaDao.getBySourceAndUrl(sourceId, manga.url)
        if (existing != null) {
            if (existing.coverUrl == null && manga.coverUrl != null) {
                mangaDao.update(existing.copy(coverUrl = manga.coverUrl))
            }
            return@withContext existing.id
        }
        mangaDao.insert(
            MangaEntity(
                sourceId = sourceId,
                url = manga.url,
                title = manga.title,
                coverUrl = manga.coverUrl,
                author = manga.author,
                artist = manga.artist,
                description = manga.description,
                genres = manga.genres,
                status = manga.status.toModel(),
                favorite = false,
                dateAdded = 0,
                readerMode = manga.readingDirection?.toReaderMode(),
                originalLang = manga.originalLang,
                detailsUpdatedAt = 0,
            ),
        )
    }

    /** Fetches details and chapters from the source. */
    suspend fun refresh(mangaId: Long) = withContext(io) {
        val entity = mangaDao.getById(mangaId) ?: return@withContext
        val source = sourceManager.getCatalog(entity.sourceId)
        val details = source.getDetails(entity.toModel().toSManga())
        mangaDao.update(
            entity.copy(
                title = details.title,
                coverUrl = details.coverUrl ?: entity.coverUrl,
                author = details.author,
                artist = details.artist,
                description = details.description,
                genres = details.genres,
                status = details.status.toModel(),
                // The user's explicit choice wins over the source's hint.
                readerMode = entity.readerMode ?: details.readingDirection?.toReaderMode(),
                originalLang = details.originalLang ?: entity.originalLang,
                detailsUpdatedAt = System.currentTimeMillis(),
            ),
        )
        val chapters = source.getChapters(details)
        chapterDao.sync(mangaId, chapters.mapIndexed { index, chapter -> chapter.toEntity(mangaId, index) })
    }

    suspend fun needsRefresh(mangaId: Long): Boolean =
        mangaDao.getById(mangaId)?.detailsUpdatedAt == 0L || chapterDao.getByManga(mangaId).isEmpty()

    suspend fun setFavorite(mangaId: Long, favorite: Boolean) {
        mangaDao.setFavorite(mangaId, favorite, if (favorite) System.currentTimeMillis() else 0)
    }

    /** Language of the text on the pages; null lets OCR detect it. */
    suspend fun setOriginalLang(mangaId: Long, lang: String?) {
        mangaDao.setOriginalLang(mangaId, lang)
    }

    suspend fun setReaderMode(mangaId: Long, mode: ReaderMode?) {
        mangaDao.setReaderMode(mangaId, mode?.name)
    }

    suspend fun getPages(manga: Manga, chapter: Chapter): List<SPage> = withContext(io) {
        sourceManager.getCatalog(manga.sourceId).getPages(chapter.toSChapter())
    }
}

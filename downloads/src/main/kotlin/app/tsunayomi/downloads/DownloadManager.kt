package app.tsunayomi.downloads

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import app.tsunayomi.core.database.dao.DownloadDao
import app.tsunayomi.core.database.entity.DownloadEntity
import app.tsunayomi.core.datastore.UserPreferencesDataSource
import app.tsunayomi.core.image.ArchiveReader
import app.tsunayomi.core.image.LocalPageUri
import app.tsunayomi.core.model.ChapterDownload
import app.tsunayomi.core.model.DownloadState
import app.tsunayomi.data.MangaRepository
import app.tsunayomi.source.api.SPage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Queues, cancels and deletes chapter downloads, and serves downloaded chapters to the reader. */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val storage: DownloadStorage,
    private val archives: ArchiveReader,
    private val preferences: UserPreferencesDataSource,
    private val mangaRepository: MangaRepository,
) {
    fun observe(mangaId: Long): Flow<Map<Long, ChapterDownload>> =
        dao.observeForManga(mangaId).map { list -> list.associate { it.chapterId to it.toModel() } }

    /** Every download (any title), for screens that mix titles such as Updates. */
    fun observeAll(): Flow<Map<Long, ChapterDownload>> =
        dao.observeAll().map { list -> list.associate { it.chapterId to it.toModel() } }

    fun observeActive(): Flow<List<ChapterDownload>> = dao.observeActive().map { list -> list.map { it.toModel() } }

    /** Queues chapters and adds the title to the library: downloaded chapters should be found there, not in the source. */
    suspend fun enqueue(mangaId: Long, chapterIds: List<Long>) {
        if (chapterIds.isEmpty()) return
        if (mangaRepository.getManga(mangaId)?.favorite == false) mangaRepository.setFavorite(mangaId, true)
        val translate = preferences.settings.first().translateOnDownload
        val now = System.currentTimeMillis()
        dao.enqueue(
            chapterIds.mapIndexed { index, chapterId ->
                // Distinct timestamps keep the queue in the order the chapters were given.
                DownloadEntity(chapterId, mangaId, DownloadState.Queued.name, translate = translate, createdAt = now + index)
            },
        )
        schedule()
    }

    suspend fun retry(chapterId: Long) {
        dao.retry(chapterId)
        schedule()
    }

    /** Cancels a queued or running download, or deletes a finished one. The worker notices the row is gone. */
    suspend fun remove(mangaId: Long, chapterIds: List<Long>) {
        dao.delete(chapterIds)
        chapterIds.forEach { storage.delete(mangaId, it) }
    }

    suspend fun removeAll(mangaId: Long) {
        remove(mangaId, dao.getForManga(mangaId).map { it.chapterId })
    }

    /** Pages of a fully downloaded chapter, or null if it is not downloaded (or the file went missing). */
    suspend fun downloadedPages(mangaId: Long, chapterId: Long): List<SPage>? {
        val download = dao.get(chapterId) ?: return null
        if (download.state != DownloadState.Done.name && download.state != DownloadState.Translating.name) return null
        val file = storage.chapterFile(mangaId, chapterId)
        if (!file.exists()) return null
        val uri = Uri.fromFile(file)
        return archives.imageEntries(uri).mapIndexed { index, entry ->
            SPage.Local(index, LocalPageUri.ArchiveEntry(uri, entry).toUriString())
        }
    }

    private fun schedule() {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        // One worker drains the whole queue; KEEP leaves a running one alone, it will pick up the new rows.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun DownloadEntity.toModel() = ChapterDownload(
        chapterId = chapterId,
        state = runCatching { DownloadState.valueOf(state) }.getOrDefault(DownloadState.Failed),
        pagesDone = pagesDone,
        pagesTotal = pagesTotal,
        error = error,
    )

    internal companion object {
        const val WORK_NAME = "chapter-downloads"
    }
}

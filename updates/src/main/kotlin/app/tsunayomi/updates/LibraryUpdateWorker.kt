package app.tsunayomi.updates

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.tsunayomi.core.datastore.UserPreferencesDataSource
import app.tsunayomi.data.LibraryRepository
import app.tsunayomi.data.MangaRepository
import app.tsunayomi.downloads.DownloadManager
import app.tsunayomi.source.api.SourceNotConfiguredException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Refreshes every library title's chapter list, one title at a time (gentle on sources' rate limits),
 * then posts one notification with what is new and optionally queues the new chapters for download.
 * A failing title (offline server, removed series) is skipped; it does not stop the rest.
 */
@HiltWorker
class LibraryUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val libraryRepository: LibraryRepository,
    private val mangaRepository: MangaRepository,
    private val downloadManager: DownloadManager,
    private val preferences: UserPreferencesDataSource,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        libraryRepository.ensureDefaultCategories()
        val readingMangaIds = libraryRepository.getReadingMangaIds()
        val ids = libraryRepository.favoriteIds()
        val found = mutableListOf<NewChapters>()
        ids.forEachIndexed { index, mangaId ->
            setForeground(UpdateNotifications.progress(applicationContext, index, ids.size))
            try {
                val newIds = mangaRepository.refresh(mangaId)
                if (newIds.isNotEmpty()) {
                    val manga = mangaRepository.getManga(mangaId) ?: return@forEachIndexed
                    val chapters = mangaRepository.getChaptersById(newIds).sortedBy { it.sourceOrder }
                    if (mangaId in readingMangaIds) {
                        found += NewChapters(mangaId, manga.title, chapters.map { it.name })
                        if (preferences.settings.first().autoDownloadNewChapters) downloadManager.enqueue(mangaId, chapters.map { it.id })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SourceNotConfiguredException) {
                // e.g. Komga server removed from settings; nothing to update
            } catch (e: Exception) {
                // One broken title must not stop the others.
            }
        }
        UpdateNotifications.newChapters(applicationContext, found)
        return Result.success()
    }
}

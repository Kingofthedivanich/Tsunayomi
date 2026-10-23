package app.tsunayomi.downloads

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.tsunayomi.core.database.dao.ChapterDao
import app.tsunayomi.core.database.dao.DownloadDao
import app.tsunayomi.core.database.dao.MangaDao
import app.tsunayomi.core.database.entity.ChapterEntity
import app.tsunayomi.core.database.entity.DownloadEntity
import app.tsunayomi.core.database.entity.MangaEntity
import app.tsunayomi.core.image.ArchiveReader
import app.tsunayomi.core.model.DownloadState
import app.tsunayomi.core.network.await
import app.tsunayomi.data.SourceManager
import app.tsunayomi.source.api.SChapter
import app.tsunayomi.source.api.SPage
import app.tsunayomi.translation.api.PageRequest
import app.tsunayomi.translation.api.PageTranslator
import app.tsunayomi.translation.core.TranslationKeys
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Drains the download queue one chapter at a time (gentle on sources' rate limits): fetch pages into a CBZ,
 * then optionally translate them into the shared translation cache. Rows deleted meanwhile are cancellations.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val sourceManager: SourceManager,
    private val client: OkHttpClient,
    private val storage: DownloadStorage,
    private val archives: ArchiveReader,
    private val pageTranslator: PageTranslator,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        dao.requeueInterrupted()
        while (true) {
            val next = dao.nextQueued() ?: return Result.success()
            process(next)
        }
    }

    private suspend fun process(download: DownloadEntity) {
        val chapter = chapterDao.getById(download.chapterId)
        val manga = mangaDao.getById(download.mangaId)
        if (chapter == null || manga == null) {
            dao.delete(listOf(download.chapterId))
            return
        }
        val file = storage.chapterFile(manga.id, chapter.id)
        try {
            val total = if (file.exists()) {
                archives.imageEntries(Uri.fromFile(file)).size
            } else {
                fetchChapter(download, manga, chapter, file)
            } ?: return // cancelled
            var note: String? = null
            if (download.translate) {
                note = translateChapter(download, manga, chapter, file, total)
                if (dao.get(download.chapterId) == null) return
            }
            dao.setDone(download.chapterId, file.path, total, note)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            storage.partialFile(manga.id, chapter.id).delete()
            if (dao.get(download.chapterId) != null) dao.setFailed(download.chapterId, e.message ?: e.toString())
        }
    }

    /** Downloads every page into a .part file and renames it when complete; null if the user cancelled. */
    private suspend fun fetchChapter(download: DownloadEntity, manga: MangaEntity, chapter: ChapterEntity, file: File): Int? {
        val source = sourceManager.getCatalog(manga.sourceId)
        dao.setProgress(chapter.id, DownloadState.Downloading.name, 0, 0)
        val pages = source.getPages(SChapter(chapter.url, chapter.name, chapter.number, chapter.uploadedAt, chapter.scanlator))
        val partial = storage.partialFile(manga.id, chapter.id).apply { parentFile?.mkdirs() }
        ZipOutputStream(partial.outputStream().buffered()).use { zip ->
            // Images are already compressed; storing them is faster and the file is no bigger.
            zip.setLevel(Deflater.NO_COMPRESSION)
            pages.forEachIndexed { index, page ->
                if (dao.get(download.chapterId) == null) {
                    zip.close()
                    partial.delete()
                    return null
                }
                setForeground(
                    DownloadNotifications.foregroundInfo(
                        applicationContext, id, manga.title,
                        applicationContext.getString(R.string.downloads_progress, chapter.name, index + 1, pages.size),
                        index, pages.size,
                    ),
                )
                val (bytes, extension) = fetchPage(page)
                zip.putNextEntry(ZipEntry(PageFiles.entryName(index, extension)))
                zip.write(bytes)
                zip.closeEntry()
                dao.setProgress(chapter.id, DownloadState.Downloading.name, index + 1, pages.size)
            }
        }
        if (!partial.renameTo(file)) throw IOException("Could not save ${file.name}")
        return pages.size
    }

    private suspend fun fetchPage(page: SPage): Pair<ByteArray, String> = when (page) {
        is SPage.Remote -> {
            val request = Request.Builder().url(page.imageUrl).apply { page.headers.forEach { (k, v) -> header(k, v) } }.build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for page ${page.index + 1}")
                response.body.bytes() to PageFiles.extension(response.header("Content-Type"), page.imageUrl)
            }
        }
        is SPage.Local -> throw IOException("Local chapters are already available offline")
    }

    /**
     * Translates the downloaded pages in windows, the same way the reader does, into the shared cache.
     * Returns a note when translation failed: the chapter stays downloaded and is translated when read online.
     */
    private suspend fun translateChapter(
        download: DownloadEntity,
        manga: MangaEntity,
        chapter: ChapterEntity,
        file: File,
        total: Int,
    ): String? {
        val uri = Uri.fromFile(file)
        val entries = archives.imageEntries(uri)
        val sourceLang = TranslationKeys.sourceLanguage(chapter.language, manga.originalLang)
        return try {
            for (start in entries.indices step TRANSLATION_WINDOW) {
                if (dao.get(download.chapterId) == null) return null
                dao.setProgress(chapter.id, DownloadState.Translating.name, start, total)
                setForeground(
                    DownloadNotifications.foregroundInfo(
                        applicationContext, id, manga.title,
                        applicationContext.getString(R.string.downloads_translating, chapter.name, start + 1, total),
                        start, total,
                    ),
                )
                val window = start until minOf(start + TRANSLATION_WINDOW, entries.size)
                pageTranslator.translate(
                    pages = window.map { index ->
                        PageRequest(TranslationKeys.page(manga.sourceId, chapter.url, index)) { decode(uri, entries[index]) }
                    },
                    sourceLang = sourceLang,
                    mangaId = manga.id,
                )
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            applicationContext.getString(R.string.downloads_translation_failed, e.message ?: e.toString())
        }
    }

    /** Decodes a page at most [OCR_MAX_SIZE] on its long side, like the reader does for translation. */
    private suspend fun decode(uri: Uri, entry: String): Bitmap {
        val bytes = archives.readEntry(uri, entry)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= OCR_MAX_SIZE) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Could not decode $entry")
    }

    private companion object {
        const val TRANSLATION_WINDOW = 10
        const val OCR_MAX_SIZE = 2048
    }
}

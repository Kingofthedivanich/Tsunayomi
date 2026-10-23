package app.tsunayomi.downloads

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Where downloaded chapters live: one CBZ per chapter in app storage (removed with the app, no permission needed). */
@Singleton
class DownloadStorage @Inject constructor(@ApplicationContext context: Context) {
    private val root = File(context.filesDir, "downloads")

    fun chapterFile(mangaId: Long, chapterId: Long): File = File(File(root, mangaId.toString()), "$chapterId.cbz")

    /** Written first, renamed when complete, so a half-downloaded chapter never looks finished. */
    fun partialFile(mangaId: Long, chapterId: Long): File = File(chapterFile(mangaId, chapterId).path + ".part")

    fun delete(mangaId: Long, chapterId: Long) {
        chapterFile(mangaId, chapterId).delete()
        partialFile(mangaId, chapterId).delete()
    }
}

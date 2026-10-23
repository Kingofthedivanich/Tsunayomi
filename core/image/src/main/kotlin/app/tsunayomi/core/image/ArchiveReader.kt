package app.tsunayomi.core.image

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import app.tsunayomi.core.common.Dispatcher
import app.tsunayomi.core.common.NaturalOrderComparator
import app.tsunayomi.core.common.TsunayomiDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Random access to ZIP/CBZ archives behind content:// URIs.
 * java.util.zip.ZipFile needs a real file path, so we read through the descriptor's FileChannel instead.
 * A few archives stay open: a reader flips through one chapter at a time.
 */
@Singleton
class ArchiveReader @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(TsunayomiDispatcher.IO) private val io: CoroutineDispatcher,
) {
    private val openArchives = object : LruCache<Uri, OpenArchive>(MAX_OPEN) {
        override fun entryRemoved(evicted: Boolean, key: Uri, oldValue: OpenArchive, newValue: OpenArchive?) {
            oldValue.close()
        }
    }

    /** Image entry names in reading order. */
    suspend fun imageEntries(file: Uri): List<String> = withArchive(file) { archive ->
        archive.zip.entries.asSequence()
            .filter { !it.isDirectory && ImageFiles.isImage(it.name) && !isJunk(it.name) }
            .map { it.name }
            .sortedWith(NaturalOrderComparator)
            .toList()
    }

    suspend fun readEntry(file: Uri, entry: String): ByteArray = withArchive(file) { archive ->
        val zipEntry = archive.zip.getEntry(entry) ?: throw FileNotFoundException("$entry in $file")
        archive.zip.getInputStream(zipEntry).use { it.readBytes() }
    }

    /** Reads a small text file such as ComicInfo.xml, matching the name case-insensitively anywhere in the archive. */
    suspend fun readTextOrNull(file: Uri, fileName: String): String? = withArchive(file) { archive ->
        val entry = archive.zip.entries.asSequence()
            .firstOrNull { !it.isDirectory && it.name.substringAfterLast('/').equals(fileName, ignoreCase = true) }
            ?: return@withArchive null
        archive.zip.getInputStream(entry).use { it.readBytes().decodeToString() }
    }

    private suspend fun <T> withArchive(file: Uri, block: (OpenArchive) -> T): T = withContext(io) {
        val archive = synchronized(openArchives) {
            openArchives[file] ?: open(file).also { openArchives.put(file, it) }
        }
        // ZipFile reads through positional channel reads, but entry streams share inflater state: serialize per archive.
        synchronized(archive) { block(archive) }
    }

    private fun open(file: Uri): OpenArchive {
        val descriptor = context.contentResolver.openFileDescriptor(file, "r")
            ?: throw FileNotFoundException(file.toString())
        return try {
            val channel = FileInputStream(descriptor.fileDescriptor).channel
            OpenArchive(descriptor, ZipFile.builder().setSeekableByteChannel(channel).get())
        } catch (e: Exception) {
            descriptor.close()
            throw e
        }
    }

    private fun isJunk(name: String): Boolean =
        name.startsWith("__MACOSX/") || name.substringAfterLast('/').startsWith(".")

    private class OpenArchive(val descriptor: ParcelFileDescriptor, val zip: ZipFile) : Closeable {
        override fun close() {
            runCatching { zip.close() }
            runCatching { descriptor.close() }
        }
    }

    private companion object {
        const val MAX_OPEN = 3
    }
}

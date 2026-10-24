package app.tsunayomi.core.image

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.collection.LruCache
import app.tsunayomi.core.common.Dispatcher
import app.tsunayomi.core.common.NaturalOrderComparator
import app.tsunayomi.core.common.TsunayomiDispatcher
import com.github.junrar.Archive
import com.github.junrar.io.SeekableReadOnlyByteChannel
import com.github.junrar.volume.Volume
import com.github.junrar.volume.VolumeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Random access to ZIP/CBZ, CBR/RAR, and EPUB archives behind content:// or file:// URIs.
 * Uses Commons Compress for ZIP/CBZ/EPUB and JUnrar for CBR/RAR.
 * All I/O operates via seekable channels on file descriptors without loading full archives into RAM.
 */
@Singleton
class ArchiveReader @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(TsunayomiDispatcher.IO) private val io: CoroutineDispatcher,
) {
    private val openArchives = object : LruCache<Uri, ArchiveHandle>(MAX_OPEN) {
        override fun entryRemoved(evicted: Boolean, key: Uri, oldValue: ArchiveHandle, newValue: ArchiveHandle?) {
            oldValue.close()
        }
    }

    /** Image entry names in reading order. */
    suspend fun imageEntries(file: Uri): List<String> = withArchive(file) { it.imageEntries() }

    suspend fun readEntry(file: Uri, entry: String): ByteArray = withArchive(file) { it.readEntry(entry) }

    /** Reads a small text file such as ComicInfo.xml, matching the name case-insensitively anywhere in the archive. */
    suspend fun readTextOrNull(file: Uri, fileName: String): String? = withArchive(file) { it.readTextOrNull(fileName) }

    /** Reads EPUB metadata (title, author, description, cover, reading direction) if the archive is an EPUB. */
    suspend fun readEpubMetadata(file: Uri): EpubMetadata? = withArchive(file) { it.readEpubMetadata() }

    private suspend fun <T> withArchive(file: Uri, block: (ArchiveHandle) -> T): T = withContext(io) {
        val archive = synchronized(openArchives) {
            openArchives[file] ?: open(file).also { openArchives.put(file, it) }
        }
        synchronized(archive) { block(archive) }
    }

    private fun open(file: Uri): ArchiveHandle {
        val descriptor = context.contentResolver.openFileDescriptor(file, "r")
            ?: throw FileNotFoundException(file.toString())
        return try {
            val channel = FileInputStream(descriptor.fileDescriptor).channel
            val isRar = isRarArchive(file, channel)
            channel.position(0)
            if (isRar) {
                val volumeManager = SingleVolumeManager(channel, channel.size())
                val rar = Archive(volumeManager, null, null)
                RarArchiveHandle(descriptor, channel, rar)
            } else {
                val zip = ZipFile.builder().setSeekableByteChannel(channel).get()
                ZipArchiveHandle(descriptor, zip)
            }
        } catch (e: Exception) {
            descriptor.close()
            throw e
        }
    }

    private fun isRarArchive(file: Uri, channel: FileChannel): Boolean {
        if (channel.size() >= 4) {
            val buffer = ByteBuffer.allocate(4)
            channel.position(0)
            val read = channel.read(buffer)
            buffer.flip()
            if (read >= 4 &&
                buffer.get() == 0x52.toByte() && // 'R'
                buffer.get() == 0x61.toByte() && // 'a'
                buffer.get() == 0x72.toByte() && // 'r'
                buffer.get() == 0x21.toByte()    // '!'
            ) {
                return true
            }
        }
        val path = file.path?.lowercase().orEmpty()
        return path.endsWith(".cbr") || path.endsWith(".rar")
    }

    private sealed interface ArchiveHandle : Closeable {
        fun imageEntries(): List<String>
        fun readEntry(entry: String): ByteArray
        fun readTextOrNull(fileName: String): String?
        fun readEpubMetadata(): EpubMetadata? = null
    }

    private class ZipArchiveHandle(
        private val descriptor: ParcelFileDescriptor,
        private val zip: ZipFile,
    ) : ArchiveHandle {
        override fun imageEntries(): List<String> {
            if (EpubReader.isEpub(zip)) {
                val spineImages = EpubReader.getSpineImageEntries(zip)
                if (!spineImages.isNullOrEmpty()) {
                    return spineImages
                }
            }
            return zip.entries.asSequence()
                .filter { !it.isDirectory && ImageFiles.isImage(it.name) && !isJunk(it.name) }
                .map { it.name }
                .sortedWith(NaturalOrderComparator)
                .toList()
        }

        override fun readEntry(entry: String): ByteArray {
            val zipEntry = zip.getEntry(entry)
                ?: zip.entries.asSequence().firstOrNull { it.name.equals(entry, ignoreCase = true) }
                ?: throw FileNotFoundException("$entry in zip archive")
            return zip.getInputStream(zipEntry).use { it.readBytes() }
        }

        override fun readTextOrNull(fileName: String): String? {
            val entry = zip.entries.asSequence()
                .firstOrNull { !it.isDirectory && it.name.substringAfterLast('/').equals(fileName, ignoreCase = true) }
                ?: return null
            return zip.getInputStream(entry).use { it.readBytes().decodeToString() }
        }

        override fun readEpubMetadata(): EpubMetadata? {
            if (!EpubReader.isEpub(zip)) return null
            return EpubReader.getEpubMetadata(zip)
        }

        override fun close() {
            runCatching { zip.close() }
            runCatching { descriptor.close() }
        }
    }

    private class RarArchiveHandle(
        private val descriptor: ParcelFileDescriptor,
        private val channel: FileChannel,
        private val archive: Archive,
    ) : ArchiveHandle {
        override fun imageEntries(): List<String> {
            return archive.fileHeaders.asSequence()
                .filter { !it.isDirectory }
                .map { it.fileName.replace('\\', '/') }
                .filter { ImageFiles.isImage(it) && !isJunk(it) }
                .sortedWith(NaturalOrderComparator)
                .toList()
        }

        override fun readEntry(entry: String): ByteArray {
            val normalized = entry.replace('\\', '/')
            val header = archive.fileHeaders.firstOrNull {
                it.fileName.replace('\\', '/').equals(normalized, ignoreCase = true)
            } ?: throw FileNotFoundException("$entry in rar archive")

            val out = ByteArrayOutputStream()
            archive.extractFile(header, out)
            return out.toByteArray()
        }

        override fun readTextOrNull(fileName: String): String? {
            val header = archive.fileHeaders.firstOrNull {
                !it.isDirectory && it.fileName.replace('\\', '/').substringAfterLast('/').equals(fileName, ignoreCase = true)
            } ?: return null

            val out = ByteArrayOutputStream()
            archive.extractFile(header, out)
            return out.toByteArray().decodeToString()
        }

        override fun close() {
            runCatching { archive.close() }
            runCatching { channel.close() }
            runCatching { descriptor.close() }
        }
    }

    private class SingleVolumeManager(
        private val channel: FileChannel,
        private val archiveLength: Long,
    ) : VolumeManager {
        override fun nextVolume(archiveInstance: Archive, lastVolume: Volume?): Volume? {
            if (lastVolume != null) return null
            return object : Volume {
                override fun getChannel(): SeekableReadOnlyByteChannel = ChannelSeekableByteChannel(this@SingleVolumeManager.channel)
                override fun getLength(): Long = this@SingleVolumeManager.archiveLength
                override fun getArchive(): Archive = archiveInstance
            }
        }
    }

    private class ChannelSeekableByteChannel(
        private val channel: FileChannel,
    ) : SeekableReadOnlyByteChannel {
        override fun getPosition(): Long = channel.position()

        override fun setPosition(pos: Long) {
            channel.position(pos)
        }

        override fun read(): Int {
            val buf = ByteBuffer.allocate(1)
            val read = channel.read(buf)
            return if (read <= 0) -1 else buf.get(0).toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, off: Int, count: Int): Int {
            if (count == 0) return 0
            val bb = ByteBuffer.wrap(buffer, off, count)
            return channel.read(bb)
        }

        override fun readFully(buffer: ByteArray, count: Int): Int {
            var total = 0
            val bb = ByteBuffer.wrap(buffer, 0, count)
            while (total < count) {
                val read = channel.read(bb)
                if (read == -1) break
                total += read
            }
            return total
        }

        override fun close() {
            // Lifecycle managed by ArchiveHandle
        }
    }

    private companion object {
        const val MAX_OPEN = 3

        fun isJunk(name: String): Boolean =
            name.startsWith("__MACOSX/") || name.substringAfterLast('/').startsWith(".")
    }
}

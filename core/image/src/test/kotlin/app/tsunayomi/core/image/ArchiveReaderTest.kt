package app.tsunayomi.core.image

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ArchiveReaderTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = mockk<Context>()
    private val contentResolver = mockk<ContentResolver>()

    init {
        every { context.contentResolver } returns contentResolver
    }

    @Test
    fun `reads zip and cbz images in natural reading order and ignores junk`(@TempDir tempDir: File) = runTest(testDispatcher) {
        val cbzFile = File(tempDir, "chapter1.cbz")
        createZip(cbzFile) { zip ->
            // Non-alphabetical insertion order
            zip.putArchiveEntry(ZipArchiveEntry("010.jpg"))
            zip.write(byteArrayOf(10))
            zip.closeArchiveEntry()

            zip.putArchiveEntry(ZipArchiveEntry("001.jpg"))
            zip.write(byteArrayOf(1))
            zip.closeArchiveEntry()

            zip.putArchiveEntry(ZipArchiveEntry("002.jpg"))
            zip.write(byteArrayOf(2))
            zip.closeArchiveEntry()

            zip.putArchiveEntry(ZipArchiveEntry("__MACOSX/._001.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeArchiveEntry()

            zip.putArchiveEntry(ZipArchiveEntry(".DS_Store"))
            zip.write(byteArrayOf(0))
            zip.closeArchiveEntry()

            zip.putArchiveEntry(ZipArchiveEntry("ComicInfo.xml"))
            zip.write("<ComicInfo><Series>One Piece</Series></ComicInfo>".toByteArray())
            zip.closeArchiveEntry()
        }

        val uri = mockUriForFile(cbzFile)
        val reader = ArchiveReader(context, testDispatcher)

        val entries = reader.imageEntries(uri)
        assertEquals(listOf("001.jpg", "002.jpg", "010.jpg"), entries)

        val bytes = reader.readEntry(uri, "001.jpg")
        assertArrayEquals(byteArrayOf(1), bytes)

        val comicInfo = reader.readTextOrNull(uri, "comicinfo.xml")
        assertEquals("<ComicInfo><Series>One Piece</Series></ComicInfo>", comicInfo)
    }

    @Test
    fun `reads epub images in spine order rather than alphabetical order`(@TempDir tempDir: File) = runTest(testDispatcher) {
        val epubFile = File(tempDir, "manga.epub")
        createZip(epubFile) { zip ->
            zip.putArchiveEntry(ZipArchiveEntry("META-INF/container.xml"))
            zip.write("""
                <container version="1.0">
                    <rootfiles>
                        <rootfile full-path="content.opf"/>
                    </rootfiles>
                </container>
            """.trimIndent().toByteArray())
            zip.closeArchiveEntry()

            zip.putArchiveEntry(ZipArchiveEntry("content.opf"))
            zip.write("""
                <package version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test Manga</dc:title>
                    </metadata>
                    <manifest>
                        <item id="page1" href="p1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="page2" href="p2.xhtml" media-type="application/xhtml+xml"/>
                        <item id="i1" href="img_01.jpg" media-type="image/jpeg"/>
                        <item id="i2" href="img_02.jpg" media-type="image/jpeg"/>
                    </manifest>
                    <spine page-progression-direction="rtl">
                        <itemref idref="page2"/>
                        <itemref idref="page1"/>
                    </spine>
                </package>
            """.trimIndent().toByteArray())
            zip.closeArchiveEntry()

            zip.putArchiveEntry(ZipArchiveEntry("p1.xhtml"))
            zip.write("""<html><body><img src="img_01.jpg"/></body></html>""".toByteArray())
            zip.closeArchiveEntry()

            zip.putArchiveEntry(ZipArchiveEntry("p2.xhtml"))
            zip.write("""<html><body><img src="img_02.jpg"/></body></html>""".toByteArray())
            zip.closeArchiveEntry()

            zip.putArchiveEntry(ZipArchiveEntry("img_01.jpg"))
            zip.write(byteArrayOf(1))
            zip.closeArchiveEntry()

            zip.putArchiveEntry(ZipArchiveEntry("img_02.jpg"))
            zip.write(byteArrayOf(2))
            zip.closeArchiveEntry()
        }

        val uri = mockUriForFile(epubFile)
        val reader = ArchiveReader(context, testDispatcher)

        val entries = reader.imageEntries(uri)
        assertEquals(listOf("img_02.jpg", "img_01.jpg"), entries)

        val meta = reader.readEpubMetadata(uri)
        assertNotNull(meta)
        assertEquals("Test Manga", meta?.title)
        assertEquals(true, meta?.isRightToLeft)
    }

    @Test
    fun `reads rar archive using junrar and seekable channel`(@TempDir tempDir: File) = runTest(testDispatcher) {
        val rarStream = javaClass.classLoader?.getResourceAsStream("test.rar")
        assertNotNull(rarStream, "test.rar resource fixture must exist")

        val rarFile = File(tempDir, "sample.cbr")
        rarFile.outputStream().use { out -> rarStream!!.copyTo(out) }

        val uri = mockUriForFile(rarFile)
        val reader = ArchiveReader(context, testDispatcher)

        val text = reader.readTextOrNull(uri, "bar.txt")
        assertNotNull(text)

        val entryBytes = reader.readEntry(uri, "foo/bar.txt")
        assertEquals(4, entryBytes.size)
    }

    private fun mockUriForFile(file: File): Uri {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://app.tsunayomi.test/${file.name}"
        every { uri.path } returns file.absolutePath

        every { contentResolver.openFileDescriptor(uri, "r") } answers {
            val fis = FileInputStream(file)
            val pfd = mockk<ParcelFileDescriptor>()
            every { pfd.fileDescriptor } returns fis.fd
            every { pfd.statSize } returns file.length()
            every { pfd.close() } answers { runCatching { fis.close() } }
            pfd
        }

        return uri
    }

    private fun createZip(file: File, block: (ZipArchiveOutputStream) -> Unit) {
        ZipArchiveOutputStream(FileOutputStream(file)).use(block)
    }
}

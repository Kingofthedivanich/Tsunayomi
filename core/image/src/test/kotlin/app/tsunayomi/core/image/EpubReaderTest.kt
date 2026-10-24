package app.tsunayomi.core.image

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream

class EpubReaderTest {

    @Test
    fun `resolvePath resolves relative paths and handles parent navigation`() {
        assertEquals("OEBPS/Images/01.jpg", EpubReader.resolvePath("OEBPS/", "Images/01.jpg"))
        assertEquals("OEBPS/Images/01.jpg", EpubReader.resolvePath("OEBPS/Text/", "../Images/01.jpg"))
        assertEquals("OEBPS/Images/01.jpg", EpubReader.resolvePath("OEBPS/Text/Sub/", "../../Images/01.jpg"))
        assertEquals("Images/01.jpg", EpubReader.resolvePath("OEBPS/", "/Images/01.jpg"))
        assertEquals("OEBPS/Images/01.jpg", EpubReader.resolvePath("OEBPS/", "Images/01.jpg#fragment"))
        assertEquals("OEBPS/Images/01.jpg", EpubReader.resolvePath("OEBPS/", "Images/01.jpg?query=1"))
    }

    @Test
    fun `isEpub correctly detects EPUB archives`(@TempDir tempDir: File) {
        val epubFile = File(tempDir, "test.epub")
        createZip(epubFile) { zip ->
            zip.putArchiveEntry(ZipArchiveEntry("META-INF/container.xml"))
            zip.write("""
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
            """.trimIndent().toByteArray())
            zip.closeArchiveEntry()
        }

        val normalZipFile = File(tempDir, "normal.zip")
        createZip(normalZipFile) { zip ->
            zip.putArchiveEntry(ZipArchiveEntry("001.jpg"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeArchiveEntry()
        }

        ZipFile.builder().setFile(epubFile).get().use { zip ->
            assertTrue(EpubReader.isEpub(zip))
        }

        ZipFile.builder().setFile(normalZipFile).get().use { zip ->
            assertFalse(EpubReader.isEpub(zip))
        }
    }

    @Test
    fun `getSpineImageEntries extracts pages in spine reading order`(@TempDir tempDir: File) {
        val epubFile = File(tempDir, "manga.epub")
        createZip(epubFile) { zip ->
            // 1. container.xml
            zip.putArchiveEntry(ZipArchiveEntry("META-INF/container.xml"))
            zip.write("""
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="EPUB/package.opf"/>
                    </rootfiles>
                </container>
            """.trimIndent().toByteArray())
            zip.closeArchiveEntry()

            // 2. package.opf
            zip.putArchiveEntry(ZipArchiveEntry("EPUB/package.opf"))
            zip.write("""
                <package version="3.0">
                    <metadata>
                        <dc:title>Test Manga</dc:title>
                    </metadata>
                    <manifest>
                        <item id="page1" href="pages/p1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="page2" href="pages/p2.xhtml" media-type="application/xhtml+xml"/>
                        <item id="img1" href="images/01.jpg" media-type="image/jpeg"/>
                        <item id="img2" href="images/02.jpg" media-type="image/jpeg"/>
                    </manifest>
                    <spine page-progression-direction="rtl">
                        <itemref idref="page2"/>
                        <itemref idref="page1"/>
                    </spine>
                </package>
            """.trimIndent().toByteArray())
            zip.closeArchiveEntry()

            // 3. p1.xhtml references images/01.jpg
            zip.putArchiveEntry(ZipArchiveEntry("EPUB/pages/p1.xhtml"))
            zip.write("""<html><body><img src="../images/01.jpg"/></body></html>""".toByteArray())
            zip.closeArchiveEntry()

            // 4. p2.xhtml references images/02.jpg using SVG <image xlink:href>
            zip.putArchiveEntry(ZipArchiveEntry("EPUB/pages/p2.xhtml"))
            zip.write("""<html><body><svg><image xlink:href="../images/02.jpg"/></svg></body></html>""".toByteArray())
            zip.closeArchiveEntry()

            // 5. Image files
            zip.putArchiveEntry(ZipArchiveEntry("EPUB/images/01.jpg"))
            zip.write(byteArrayOf(1, 1, 1))
            zip.closeArchiveEntry()

            zip.putArchiveEntry(ZipArchiveEntry("EPUB/images/02.jpg"))
            zip.write(byteArrayOf(2, 2, 2))
            zip.closeArchiveEntry()
        }

        ZipFile.builder().setFile(epubFile).get().use { zip ->
            val pages = EpubReader.getSpineImageEntries(zip)
            assertNotNull(pages)
            // Spine lists page2 before page1, so 02.jpg should come before 01.jpg
            assertEquals(listOf("EPUB/images/02.jpg", "EPUB/images/01.jpg"), pages)
        }
    }

    @Test
    fun `getEpubMetadata extracts title, author, description, rtl, and cover`(@TempDir tempDir: File) {
        val epubFile = File(tempDir, "metadata.epub")
        createZip(epubFile) { zip ->
            zip.putArchiveEntry(ZipArchiveEntry("META-INF/container.xml"))
            zip.write("""
                <container version="1.0">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf"/>
                    </rootfiles>
                </container>
            """.trimIndent().toByteArray())
            zip.closeArchiveEntry()

            zip.putArchiveEntry(ZipArchiveEntry("OEBPS/content.opf"))
            zip.write("""
                <package version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Frieren &amp; Beyond</dc:title>
                        <dc:creator>Kanehito Yamada</dc:creator>
                        <dc:description>&lt;p&gt;The journey &lt;b&gt;continues&lt;/b&gt;.&lt;/p&gt;</dc:description>
                    </metadata>
                    <manifest>
                        <item id="cov" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                        <item id="p1" href="images/01.jpg" media-type="image/jpeg"/>
                    </manifest>
                    <spine page-progression-direction="rtl">
                        <itemref idref="cov"/>
                        <itemref idref="p1"/>
                    </spine>
                </package>
            """.trimIndent().toByteArray())
            zip.closeArchiveEntry()

            zip.putArchiveEntry(ZipArchiveEntry("OEBPS/images/cover.jpg"))
            zip.write(byteArrayOf(9, 9, 9))
            zip.closeArchiveEntry()
        }

        ZipFile.builder().setFile(epubFile).get().use { zip ->
            val meta = EpubReader.getEpubMetadata(zip)
            assertNotNull(meta)
            assertEquals("Frieren & Beyond", meta?.title)
            assertEquals("Kanehito Yamada", meta?.author)
            assertEquals("The journey continues.", meta?.description)
            assertEquals(true, meta?.isRightToLeft)
            assertEquals("OEBPS/images/cover.jpg", meta?.coverEntry)
        }
    }

    private fun createZip(file: File, block: (ZipArchiveOutputStream) -> Unit) {
        ZipArchiveOutputStream(FileOutputStream(file)).use(block)
    }
}

package app.tsunayomi.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PageFilesTest {
    @Test
    fun `entries are zero padded and one based`() {
        assertEquals("0001.jpg", PageFiles.entryName(0, "jpg"))
        assertEquals("0120.png", PageFiles.entryName(119, "png"))
    }

    @Test
    fun `extension comes from content type, then url`() {
        assertEquals("webp", PageFiles.extension("image/webp; charset=binary", "https://x/1"))
        assertEquals("png", PageFiles.extension(null, "https://node/data/hash/x1-abc.png?token=1"))
        assertEquals("jpg", PageFiles.extension("application/octet-stream", "https://komga/api/v1/books/B/pages/3"))
    }
}

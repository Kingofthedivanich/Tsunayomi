package app.tsunayomi.translation.mangaocr

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.FloatBuffer

class MangaOcrTextTest {
    private val vocab = listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "[MASK]", "な", "ん", "だ", "!", "?", ".", "…", " ", "##と", "A")

    @Test
    fun `pixels become grayscale in -1 to 1 on three planes`() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val red = 0xFFFF0000.toInt()

        val out = MangaOcrText.normalize(intArrayOf(white, black, red))

        val redGray = (299 * 255 / 1000) / 255f * 2 - 1
        assertArrayEquals(floatArrayOf(1f, -1f, redGray, 1f, -1f, redGray, 1f, -1f, redGray), out, 1e-5f)
    }

    @Test
    fun `argmax reads the last position`() {
        val logits = FloatBuffer.wrap(floatArrayOf(9f, 0f, 0f, 0f, 1f, 5f))

        assertEquals(2, MangaOcrText.argmaxLast(logits, length = 2, vocab = 3))
    }

    @Test
    fun `decoding drops special tokens and spaces and widens ascii like manga-ocr`() {
        val ids = listOf(5L, 6L, 12L, 7L, 13L, 8L, 9L, 3L, 14L)

        assertEquals("なんだと！？Ａ", MangaOcrText.decode(ids, vocab))
    }

    @Test
    fun `ellipses become dots`() {
        assertEquals("なん．．．", MangaOcrText.decode(listOf(5L, 6L, 11L), vocab))
        assertEquals("な．．", MangaOcrText.decode(listOf(5L, 10L, 10L), vocab))
    }
}

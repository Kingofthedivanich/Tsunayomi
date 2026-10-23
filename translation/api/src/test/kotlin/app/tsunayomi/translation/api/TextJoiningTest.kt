package app.tsunayomi.translation.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextJoiningTest {
    @Test
    fun `latin lines join with spaces`() {
        assertEquals("I can't believe it!", TextJoining.join(listOf("I can't", " believe it! ")))
    }

    @Test
    fun `hyphenated words are glued back`() {
        assertEquals("We need to translate this", TextJoining.join(listOf("We need to trans-", "late this")))
    }

    @Test
    fun `cjk lines join without spaces`() {
        assertEquals("こんにちは世界", TextJoining.join(listOf("こんにちは", "世界")))
        assertEquals("안녕하세요", TextJoining.join(listOf("안녕", "하세요")))
    }

    @Test
    fun `noise is not meaningful`() {
        assertFalse(TextJoining.isMeaningful("!!"))
        assertFalse(TextJoining.isMeaningful("a"))
        assertTrue(TextJoining.isMeaningful("猫"))
        assertTrue(TextJoining.isMeaningful("OK"))
        assertFalse(TextJoining.isMeaningful("32"))
        assertFalse(TextJoining.isMeaningful("1/6"))
    }

    @Test
    fun `all caps becomes sentence case`() {
        assertEquals("Xin chào. Bạn khỏe không?", TextJoining.normalizeCase("XIN CHÀO. BẠN KHỎE KHÔNG?"))
        assertEquals("Normal Text stays", TextJoining.normalizeCase("Normal Text stays"))
        assertEquals("OK!", TextJoining.normalizeCase("OK!"))
        assertEquals("こんにちは", TextJoining.normalizeCase("こんにちは"))
    }

    @Test
    fun `cjk ratio`() {
        assertEquals(1f, TextJoining.cjkRatio("こんにちは"))
        assertEquals(0f, TextJoining.cjkRatio("HELLO 32"))
        assertTrue(TextJoining.cjkRatio("HELLO ん") < 0.2f)
    }
}

package app.tsunayomi.translation.mlkit

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
    }
}

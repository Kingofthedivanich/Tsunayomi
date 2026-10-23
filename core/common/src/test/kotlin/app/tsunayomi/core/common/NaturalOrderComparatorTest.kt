package app.tsunayomi.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NaturalOrderComparatorTest {
    private fun sorted(vararg names: String) = names.sortedWith(NaturalOrderComparator)

    @Test
    fun `numbers sort by value`() {
        assertEquals(
            listOf("page2.jpg", "page10.jpg", "page100.jpg"),
            sorted("page100.jpg", "page2.jpg", "page10.jpg"),
        )
    }

    @Test
    fun `leading zeros do not change order`() {
        assertEquals(listOf("001.png", "2.png", "010.png"), sorted("010.png", "2.png", "001.png"))
    }

    @Test
    fun `letters compare case-insensitively`() {
        assertEquals(listOf("a1", "B1", "c1"), sorted("c1", "B1", "a1"))
    }

    @Test
    fun `chapter names with decimals`() {
        assertEquals(
            listOf("Chapter 1", "Chapter 1.5", "Chapter 2", "Chapter 11"),
            sorted("Chapter 11", "Chapter 2", "Chapter 1.5", "Chapter 1"),
        )
    }

    @Test
    fun `prefix sorts before longer name`() {
        assertEquals(listOf("vol", "vol 1"), sorted("vol 1", "vol"))
    }
}

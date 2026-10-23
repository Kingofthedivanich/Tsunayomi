package app.tsunayomi.translation.core

import app.tsunayomi.translation.api.NormalizedBox
import app.tsunayomi.translation.api.RecognizedBlock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BubbleGroupingTest {
    private fun block(text: String, left: Float, top: Float, right: Float, bottom: Float, line: Float = 0.02f) =
        RecognizedBlock(NormalizedBox(left, top, right, bottom), text, lineHeight = line)

    @Test
    fun `stacked pieces of one bubble are merged in reading order`() {
        val result = BubbleGrouping.group(
            listOf(
                block("learn to be", 0.40f, 0.125f, 0.60f, 0.145f),
                block("You should", 0.42f, 0.10f, 0.58f, 0.12f),
                block("brave.", 0.45f, 0.15f, 0.55f, 0.17f),
            ),
        )
        assertEquals(listOf("You should learn to be brave."), result.map { it.text })
        assertEquals(NormalizedBox(0.40f, 0.10f, 0.60f, 0.17f), result.single().box)
    }

    @Test
    fun `separate bubbles stay apart`() {
        val result = BubbleGrouping.group(
            listOf(
                block("Top bubble", 0.1f, 0.10f, 0.3f, 0.12f),
                block("Far below", 0.1f, 0.40f, 0.3f, 0.42f),
                block("Same row, other side", 0.7f, 0.10f, 0.9f, 0.12f),
            ),
        )
        assertEquals(listOf("Top bubble", "Same row, other side", "Far below"), result.map { it.text })
    }

    @Test
    fun `vertical columns side by side are not merged`() {
        val result = BubbleGrouping.group(
            listOf(
                block("こんにちは", 0.50f, 0.10f, 0.53f, 0.30f, line = 0.03f),
                block("せかい", 0.46f, 0.10f, 0.49f, 0.25f, line = 0.03f),
            ),
        )
        assertEquals(2, result.size)
    }
}

package app.tsunayomi.translation.core

import app.tsunayomi.translation.api.NormalizedBox
import app.tsunayomi.translation.api.RecognizedBlock
import app.tsunayomi.translation.api.TextJoining
import kotlin.math.max
import kotlin.math.min

/**
 * OCR often returns one speech bubble as several blocks (one per line group). Translating them apart loses
 * the sentence, so blocks stacked closely on top of each other are merged back into one.
 */
object BubbleGrouping {
    fun group(blocks: List<RecognizedBlock>): List<RecognizedBlock> {
        if (blocks.size < 2) return blocks
        val parent = IntArray(blocks.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        for (i in blocks.indices) {
            for (j in i + 1 until blocks.size) {
                if (belongTogether(blocks[i], blocks[j])) parent[find(i)] = find(j)
            }
        }
        return blocks.indices
            .groupBy(::find)
            .values
            .map { members -> merge(members.map { blocks[it] }) }
            // Keep a stable top-to-bottom order for the translator.
            .sortedWith(compareBy({ it.box.top }, { it.box.left }))
    }

    /** Vertically stacked with a gap under about one line, and horizontally overlapping. */
    internal fun belongTogether(a: RecognizedBlock, b: RecognizedBlock): Boolean {
        val (upper, lower) = if (a.box.top <= b.box.top) a to b else b to a
        val gap = lower.box.top - upper.box.bottom
        val line = max(upper.lineHeight, lower.lineHeight)
        if (gap > line * MAX_GAP_LINES) return false
        // Overlapping vertically means side by side (e.g. vertical Japanese columns or two bubbles): not merged.
        if (gap < -line * 0.5f) return false
        val overlap = min(upper.box.right, lower.box.right) - max(upper.box.left, lower.box.left)
        val narrower = min(upper.box.width, lower.box.width)
        return narrower > 0 && overlap >= narrower * MIN_HORIZONTAL_OVERLAP
    }

    private fun merge(parts: List<RecognizedBlock>): RecognizedBlock {
        if (parts.size == 1) return parts.first()
        val ordered = parts.sortedBy { it.box.top }
        return RecognizedBlock(
            box = NormalizedBox(
                left = ordered.minOf { it.box.left },
                top = ordered.minOf { it.box.top },
                right = ordered.maxOf { it.box.right },
                bottom = ordered.maxOf { it.box.bottom },
            ),
            text = TextJoining.join(ordered.map { it.text }),
            lineHeight = ordered.map { it.lineHeight }.average().toFloat(),
        )
    }

    private const val MAX_GAP_LINES = 1.2f
    private const val MIN_HORIZONTAL_OVERLAP = 0.3f
}

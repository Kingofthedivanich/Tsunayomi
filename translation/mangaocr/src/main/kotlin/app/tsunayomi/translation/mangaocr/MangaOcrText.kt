package app.tsunayomi.translation.mangaocr

import java.nio.FloatBuffer

/** The parts of manga-ocr's pre- and post-processing that don't need Android. */
internal object MangaOcrText {
    const val SIZE = 224
    const val START_ID = 2L // [CLS]
    const val END_ID = 3 // [SEP]
    private const val FIRST_REAL_TOKEN = 5 // [PAD] [UNK] [CLS] [SEP] [MASK]

    /** ARGB pixels to the model input: grayscale like PIL's "L", scaled to [-1, 1], repeated over 3 channels (CHW). */
    fun normalize(argb: IntArray): FloatArray {
        val plane = argb.size
        val out = FloatArray(plane * 3)
        for (i in argb.indices) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val gray = ((r * 299 + g * 587 + b * 114) / 1000) / 255f
            val v = (gray - 0.5f) / 0.5f
            out[i] = v
            out[plane + i] = v
            out[2 * plane + i] = v
        }
        return out
    }

    /** Index of the largest logit at the last position of a [1, length, vocab] output. */
    fun argmaxLast(logits: FloatBuffer, length: Int, vocab: Int): Int {
        val offset = (length - 1) * vocab
        var best = 0
        var bestValue = Float.NEGATIVE_INFINITY
        for (i in 0 until vocab) {
            val v = logits.get(offset + i)
            if (v > bestValue) {
                bestValue = v
                best = i
            }
        }
        return best
    }

    /** Token ids to text, then manga-ocr's clean-up: no spaces, "…" as dots, ASCII as full-width characters. */
    fun decode(ids: List<Long>, vocab: List<String>): String {
        val raw = ids.filter { it >= FIRST_REAL_TOKEN && it < vocab.size }
            .joinToString("") { vocab[it.toInt()].removePrefix("##") }
        val text = raw.filterNot { it.isWhitespace() }
            .replace("…", "...")
            .replace(Regex("[・.]{2,}")) { ".".repeat(it.value.length) }
        return buildString {
            for (c in text) append(if (c in '!'..'~') (c.code + 0xFEE0).toChar() else c)
        }
    }
}

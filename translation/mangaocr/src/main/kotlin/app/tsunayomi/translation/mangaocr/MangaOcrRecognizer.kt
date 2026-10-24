package app.tsunayomi.translation.mangaocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import app.tsunayomi.translation.api.JapaneseOcrProvider
import app.tsunayomi.translation.api.NormalizedBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a cropped speech bubble with manga-ocr, which handles vertical text and furigana far better than ML Kit.
 * Greedy decoding; the decoder has no KV cache, so each step feeds the whole sequence so far.
 */
@Singleton
class MangaOcrRecognizer @Inject constructor(private val models: MangaOcrModelManager) : JapaneseOcrProvider {
    private val mutex = Mutex()
    private var loaded: Loaded? = null

    private class Loaded(val generation: Int, val encoder: OrtSession, val decoder: OrtSession, val vocab: List<String>)

    override fun isAvailable(): Boolean = models.isReady()

    override suspend fun recognize(page: Bitmap, box: NormalizedBox): String? = withContext(Dispatchers.Default) {
        val crop = crop(page, box) ?: return@withContext null
        mutex.withLock {
            val model = load() ?: return@withLock null
            val env = OrtEnvironment.getEnvironment()
            val pixels = MangaOcrText.normalize(argbPixels(crop))
            val hidden = OnnxTensor.createTensor(env, FloatBuffer.wrap(pixels), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { input ->
                model.encoder.run(mapOf("pixel_values" to input)).use { result ->
                    val tensor = result[0] as OnnxTensor
                    val shape = tensor.info.shape
                    val buffer = tensor.floatBuffer
                    FloatArray(buffer.remaining()).also { buffer.get(it) } to shape
                }
            }
            val ids = mutableListOf(MangaOcrText.START_ID)
            OnnxTensor.createTensor(env, FloatBuffer.wrap(hidden.first), hidden.second).use { states ->
                for (step in 0 until MAX_TOKENS) {
                    val next = OnnxTensor.createTensor(env, LongBuffer.wrap(ids.toLongArray()), longArrayOf(1, ids.size.toLong()))
                        .use { input ->
                            model.decoder.run(mapOf("input_ids" to input, "encoder_hidden_states" to states)).use { result ->
                                MangaOcrText.argmaxLast((result[0] as OnnxTensor).floatBuffer, ids.size, model.vocab.size)
                            }
                        }
                    if (next == MangaOcrText.END_ID) break
                    ids += next.toLong()
                }
            }
            MangaOcrText.decode(ids.drop(1), model.vocab).ifBlank { null }
        }
    }

    /** The box with a little margin, since OCR boxes hug the glyphs. */
    private fun crop(page: Bitmap, box: NormalizedBox): Bitmap? {
        val marginX = box.width * CROP_MARGIN
        val marginY = box.height * CROP_MARGIN
        val left = ((box.left - marginX) * page.width).toInt().coerceIn(0, page.width - 1)
        val top = ((box.top - marginY) * page.height).toInt().coerceIn(0, page.height - 1)
        val right = ((box.right + marginX) * page.width).toInt().coerceIn(left + 1, page.width)
        val bottom = ((box.bottom + marginY) * page.height).toInt().coerceIn(top + 1, page.height)
        if (right - left < MIN_SIDE || bottom - top < MIN_SIDE) return null
        return Bitmap.createBitmap(page, left, top, right - left, bottom - top)
    }

    private fun argbPixels(crop: Bitmap): IntArray {
        // The model was trained on grayscale crops squashed to a square, so no aspect-preserving padding.
        val scaled = Bitmap.createScaledBitmap(crop, SIZE, SIZE, true)
        return IntArray(SIZE * SIZE).also { scaled.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }
    }

    private fun load(): Loaded? {
        if (!models.isReady()) {
            close()
            return null
        }
        loaded?.takeIf { it.generation == models.generation }?.let { return it }
        close()
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
        return Loaded(
            generation = models.generation,
            encoder = env.createSession(models.file(MangaOcrModelManager.ENCODER).absolutePath, options),
            decoder = env.createSession(models.file(MangaOcrModelManager.DECODER).absolutePath, options),
            vocab = models.file(MangaOcrModelManager.VOCAB).readLines(),
        ).also { loaded = it }
    }

    private fun close() {
        loaded?.let {
            it.encoder.close()
            it.decoder.close()
        }
        loaded = null
    }

    private companion object {
        const val SIZE = MangaOcrText.SIZE

        /** Speech bubbles are short; this bounds the cost of a crop that isn't one. */
        const val MAX_TOKENS = 96

        const val CROP_MARGIN = 0.08f
        const val MIN_SIDE = 8
    }
}

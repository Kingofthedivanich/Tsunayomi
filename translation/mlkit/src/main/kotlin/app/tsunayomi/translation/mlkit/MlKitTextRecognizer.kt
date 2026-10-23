package app.tsunayomi.translation.mlkit

import android.graphics.Bitmap
import app.tsunayomi.translation.api.NormalizedBox
import app.tsunayomi.translation.api.RecognizedBlock
import app.tsunayomi.translation.api.TextJoining
import app.tsunayomi.translation.api.TextRecognizer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import com.google.mlkit.vision.text.TextRecognizer as MlKitClient

/**
 * On-device OCR with ML Kit. Text blocks roughly match speech bubbles, which is enough for a first version;
 * a dedicated bubble detector and manga-ocr for vertical Japanese can replace it later behind [TextRecognizer].
 */
@Singleton
class MlKitTextRecognizer @Inject constructor() : TextRecognizer {
    private val clients = ConcurrentHashMap<Script, MlKitClient>()

    override suspend fun recognize(page: Bitmap, sourceLang: String?): List<RecognizedBlock> {
        val image = InputImage.fromBitmap(page, 0)
        if (sourceLang != null) return read(image, page, Script.forLanguage(sourceLang))
        // Unknown language: the Japanese model reads everything, but on a Latin-script page it garbles diacritics
        // and invents kana. If it found almost no CJK, read the page again with the Latin model.
        val cjkAttempt = read(image, page, Script.Japanese)
        val cjkRatio = TextJoining.cjkRatio(cjkAttempt.joinToString("") { it.text })
        return if (cjkAttempt.isNotEmpty() && cjkRatio < LATIN_PAGE_CJK_RATIO) read(image, page, Script.Latin) else cjkAttempt
    }

    private suspend fun read(image: InputImage, page: Bitmap, script: Script): List<RecognizedBlock> {
        val client = clients.getOrPut(script) { TextRecognition.getClient(script.options()) }
        val result = client.process(image).await()
        val width = page.width.toFloat()
        val height = page.height.toFloat()
        return result.textBlocks.mapNotNull { block ->
            val rect = block.boundingBox ?: return@mapNotNull null
            val text = TextJoining.join(block.lines.map { it.text })
            if (!TextJoining.isMeaningful(text)) return@mapNotNull null
            val lineHeights = block.lines.mapNotNull { it.boundingBox?.height()?.toFloat() }
            RecognizedBlock(
                box = NormalizedBox(rect.left / width, rect.top / height, rect.right / width, rect.bottom / height),
                text = text,
                lineHeight = (lineHeights.average().takeIf { !it.isNaN() }?.toFloat() ?: rect.height().toFloat()) / height,
            )
        }
    }

    private companion object {
        /** Below this share of CJK letters a page is treated as Latin script (stray misread kana stay under it). */
        const val LATIN_PAGE_CJK_RATIO = 0.15f
    }

    private enum class Script {
        Latin, Japanese, Korean, Chinese;

        fun options(): TextRecognizerOptionsInterface = when (this) {
            Latin -> TextRecognizerOptions.DEFAULT_OPTIONS
            Japanese -> JapaneseTextRecognizerOptions.Builder().build()
            Korean -> KoreanTextRecognizerOptions.Builder().build()
            Chinese -> ChineseTextRecognizerOptions.Builder().build()
        }

        companion object {
            /** The CJK models read Latin too, so an unknown language goes to the Japanese one. */
            fun forLanguage(lang: String?): Script = when (lang?.lowercase()) {
                null, "" -> Japanese
                "ja" -> Japanese
                "ko" -> Korean
                "zh" -> Chinese
                else -> Latin
            }
        }
    }
}

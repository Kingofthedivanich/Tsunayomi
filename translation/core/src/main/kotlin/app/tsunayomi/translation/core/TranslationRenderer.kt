package app.tsunayomi.translation.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import app.tsunayomi.translation.api.TranslatedRegion
import kotlin.math.max
import kotlin.math.min

/**
 * Draws translations onto a copy of the page: each region is covered with a rounded box and the
 * translated text is fitted inside. Baking it into the image keeps zoom, pan and webtoon scrolling
 * free of any coordinate mapping.
 */
object TranslationRenderer {
    fun render(page: Bitmap, regions: List<TranslatedRegion>): Bitmap {
        val output = page.copy(Bitmap.Config.ARGB_8888, true)
        if (regions.isEmpty()) return output
        val canvas = Canvas(output)
        val width = output.width.toFloat()
        val height = output.height.toFloat()
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(245, 255, 255, 255) }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            isFakeBoldText = true
        }

        regions.filter { it.changesText() }.forEach { region ->
            val box = region.box
            // OCR boxes hug the glyphs; translations (especially into Russian) need more room.
            val padX = width * PADDING_X
            val padY = height * PADDING_Y
            val rect = RectF(
                max(0f, box.left * width - padX),
                max(0f, box.top * height - padY),
                min(width, box.right * width + padX),
                min(height, box.bottom * height + padY),
            )
            val radius = min(rect.width(), rect.height()) * 0.2f
            canvas.drawRoundRect(rect, radius, radius, boxPaint)

            val inset = max(2f, min(rect.width(), rect.height()) * 0.06f)
            val textWidth = (rect.width() - inset * 2).toInt().coerceAtLeast(1)
            val textHeight = rect.height() - inset * 2
            val layout = fitText(region.translatedText, textPaint, textWidth, textHeight, maxSize = height * MAX_TEXT_FRACTION)
            canvas.withTranslation(rect.left + inset, rect.top + inset + (textHeight - layout.height) / 2f) {
                layout.draw(this)
            }
        }
        return output
    }

    /**
     * Engines return names, numbers and credits unchanged; covering those with an identical copy only hides
     * the original lettering, so such regions are left as they are.
     */
    internal fun TranslatedRegion.changesText(): Boolean = normalize(translatedText) != normalize(sourceText)

    private fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    /** The largest text size whose wrapped layout fits [maxHeight]; binary search over sizes. */
    private fun fitText(text: String, paint: TextPaint, width: Int, maxHeight: Float, maxSize: Float): StaticLayout {
        var low = MIN_TEXT_PX
        var high = max(MIN_TEXT_PX, maxSize)
        var best = layout(text, paint, width, low)
        repeat(12) {
            val mid = (low + high) / 2f
            val candidate = layout(text, paint, width, mid)
            if (candidate.height <= maxHeight && !candidate.hasBrokenWords()) {
                best = candidate
                low = mid
            } else {
                high = mid
            }
        }
        return best
    }

    private fun layout(text: String, paint: TextPaint, width: Int, size: Float): StaticLayout {
        paint.textSize = size
        return StaticLayout.Builder.obtain(text, 0, text.length, TextPaint(paint), width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 0.95f)
            .build()
    }

    /** A word split mid-way across lines reads badly; prefer a smaller size that keeps words whole. */
    private fun StaticLayout.hasBrokenWords(): Boolean {
        for (line in 0 until lineCount - 1) {
            val end = getLineEnd(line)
            val text = this.text
            if (end in 1 until text.length && text[end - 1].isLetter() && text[end].isLetter()) return true
        }
        return false
    }

    private const val PADDING_X = 0.012f
    private const val PADDING_Y = 0.006f
    private const val MIN_TEXT_PX = 8f
    private const val MAX_TEXT_FRACTION = 0.03f
}

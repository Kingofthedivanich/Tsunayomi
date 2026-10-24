package app.tsunayomi.translation.api

import android.graphics.Bitmap

/**
 * A recognizer specialised in Japanese manga text (vertical lines, furigana). The general OCR still finds where
 * the bubbles are; this one reads each bubble again.
 */
interface JapaneseOcrProvider {
    fun isAvailable(): Boolean

    /** The text in [box] of [page], or null if nothing could be read. */
    suspend fun recognize(page: Bitmap, box: NormalizedBox): String?
}

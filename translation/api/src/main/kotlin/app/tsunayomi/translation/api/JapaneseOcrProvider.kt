package app.tsunayomi.translation.api

import android.graphics.Bitmap

/**
 * Specialized OCR provider for vertical Japanese text in manga bubbles.
 * When available, it refines text recognized by the base OCR engine for Japanese pages.
 */
interface JapaneseOcrProvider {
    fun isAvailable(): Boolean
    suspend fun recognize(crop: Bitmap): String?
}

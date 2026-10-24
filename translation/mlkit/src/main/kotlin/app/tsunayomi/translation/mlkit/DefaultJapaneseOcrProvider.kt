package app.tsunayomi.translation.mlkit

import android.graphics.Bitmap
import app.tsunayomi.translation.api.JapaneseOcrProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultJapaneseOcrProvider @Inject constructor() : JapaneseOcrProvider {
    override fun isAvailable(): Boolean = false
    override suspend fun recognize(crop: Bitmap): String? = null
}

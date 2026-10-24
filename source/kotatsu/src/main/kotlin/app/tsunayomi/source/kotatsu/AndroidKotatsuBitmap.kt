package app.tsunayomi.source.kotatsu

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import org.koitharu.kotatsu.parsers.bitmap.Bitmap as KotatsuBitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect as KotatsuRect

internal class AndroidKotatsuBitmap(
    val androidBitmap: android.graphics.Bitmap,
) : KotatsuBitmap {
    private val canvas by lazy { Canvas(androidBitmap) }
    private val paint by lazy { Paint(Paint.FILTER_BITMAP_FLAG) }

    override val width: Int get() = androidBitmap.width

    override val height: Int get() = androidBitmap.height

    override fun drawBitmap(sourceBitmap: KotatsuBitmap, src: KotatsuRect, dst: KotatsuRect) {
        val srcAndroid = (sourceBitmap as? AndroidKotatsuBitmap)?.androidBitmap ?: return
        val sR = AndroidRect(src.left, src.top, src.right, src.bottom)
        val dR = AndroidRect(dst.left, dst.top, dst.right, dst.bottom)
        canvas.drawBitmap(srcAndroid, sR, dR, paint)
    }
}

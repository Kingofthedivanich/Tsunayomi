package app.tsunayomi.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import androidx.core.graphics.createBitmap
import app.tsunayomi.core.common.Dispatcher
import app.tsunayomi.core.common.TsunayomiDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Renders PDF pages to bitmaps with the platform PdfRenderer, which allows only one open page per document. */
@Singleton
class PdfPageRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(TsunayomiDispatcher.IO) private val io: CoroutineDispatcher,
) {
    private val openDocuments = object : LruCache<Uri, PdfRenderer>(MAX_OPEN) {
        override fun entryRemoved(evicted: Boolean, key: Uri, oldValue: PdfRenderer, newValue: PdfRenderer?) {
            runCatching { oldValue.close() }
        }
    }

    suspend fun pageCount(file: Uri): Int = withRenderer(file) { it.pageCount }

    /** Renders [page] so that its width is [targetWidth] pixels, keeping the aspect ratio. */
    suspend fun render(file: Uri, page: Int, targetWidth: Int): Bitmap = withRenderer(file) { renderer ->
        renderer.openPage(page).use { pdfPage ->
            val width = targetWidth.coerceIn(MIN_WIDTH, MAX_WIDTH)
            val height = (width.toFloat() * pdfPage.height / pdfPage.width).roundToInt()
            createBitmap(width, height).apply {
                eraseColor(Color.WHITE)
                pdfPage.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    private suspend fun <T> withRenderer(file: Uri, block: (PdfRenderer) -> T): T = withContext(io) {
        val renderer = synchronized(openDocuments) {
            openDocuments[file] ?: open(file).also { openDocuments.put(file, it) }
        }
        synchronized(renderer) { block(renderer) }
    }

    private fun open(file: Uri): PdfRenderer {
        val descriptor = context.contentResolver.openFileDescriptor(file, "r")
            ?: throw FileNotFoundException(file.toString())
        return PdfRenderer(descriptor)
    }

    private companion object {
        const val MAX_OPEN = 2
        const val MIN_WIDTH = 480
        const val MAX_WIDTH = 2400
    }
}

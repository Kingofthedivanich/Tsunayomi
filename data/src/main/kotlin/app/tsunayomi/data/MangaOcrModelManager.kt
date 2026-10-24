package app.tsunayomi.data

import android.content.Context
import app.tsunayomi.core.model.MangaOcrStatus
import app.tsunayomi.core.network.await
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaOcrModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var downloadJob: Job? = null

    private val modelDir: File
        get() = File(context.filesDir, "models/manga_ocr").apply { mkdirs() }

    val modelFile: File
        get() = File(modelDir, MODEL_FILENAME)

    val vocabFile: File
        get() = File(modelDir, VOCAB_FILENAME)

    private val _status = MutableStateFlow<MangaOcrStatus>(MangaOcrStatus.NotDownloaded)
    val status: StateFlow<MangaOcrStatus> = _status.asStateFlow()

    init {
        refreshStatus()
    }

    fun isReady(): Boolean = modelFile.exists() && modelFile.length() > 10_000_000L

    fun refreshStatus() {
        if (isReady()) {
            val totalSize = modelFile.length() + if (vocabFile.exists()) vocabFile.length() else 0L
            _status.value = MangaOcrStatus.Ready(totalSize)
        } else {
            _status.value = MangaOcrStatus.NotDownloaded
        }
    }

    fun download() {
        if (downloadJob?.isActive == true || isReady()) return
        downloadJob = scope.launch {
            val tmpModel = File(modelDir, "$MODEL_FILENAME.tmp")
            val tmpVocab = File(modelDir, "$VOCAB_FILENAME.tmp")
            try {
                _status.value = MangaOcrStatus.Downloading(0f, 0L, ESTIMATED_TOTAL_BYTES)

                // 1. Download vocab
                downloadFile(VOCAB_URL, tmpVocab)

                // 2. Download ONNX model with progress
                downloadFileWithProgress(MODEL_URL, tmpModel) { downloaded, total ->
                    val actualTotal = if (total > 0L) total else ESTIMATED_TOTAL_BYTES
                    val progress = (downloaded.toFloat() / actualTotal.toFloat()).coerceIn(0f, 1f)
                    _status.value = MangaOcrStatus.Downloading(progress, downloaded, actualTotal)
                }

                // Atomic rename
                if (tmpVocab.exists()) tmpVocab.renameTo(vocabFile)
                if (tmpModel.exists()) tmpModel.renameTo(modelFile)

                refreshStatus()
            } catch (e: CancellationException) {
                tmpModel.delete()
                tmpVocab.delete()
                refreshStatus()
                throw e
            } catch (e: Exception) {
                tmpModel.delete()
                tmpVocab.delete()
                _status.value = MangaOcrStatus.Error(e.message ?: "Ошибка загрузки модели")
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
        downloadJob = null
        File(modelDir, "$MODEL_FILENAME.tmp").delete()
        File(modelDir, "$VOCAB_FILENAME.tmp").delete()
        refreshStatus()
    }

    fun delete() {
        cancel()
        modelFile.delete()
        vocabFile.delete()
        refreshStatus()
    }

    private suspend fun downloadFile(url: String, target: File) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} downloading $url")
            val body = response.body
            body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private suspend fun downloadFileWithProgress(
        url: String,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} downloading $url")
            val body = response.body
            val contentLength = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        onProgress(totalRead, contentLength)
                    }
                }
            }
        }
    }

    companion object {
        private const val MODEL_FILENAME = "manga_ocr.onnx"
        private const val VOCAB_FILENAME = "vocab.txt"
        private const val ESTIMATED_TOTAL_BYTES = 142_000_000L

        private const val MODEL_URL = "https://huggingface.co/kha-white/manga-ocr-base/resolve/main/onnx/model_quantized.onnx"
        private const val VOCAB_URL = "https://huggingface.co/kha-white/manga-ocr-base/resolve/main/vocab.txt"
    }
}

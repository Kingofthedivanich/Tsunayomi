package app.tsunayomi.translation.mangaocr

import android.content.Context
import app.tsunayomi.core.model.MangaOcrStatus
import app.tsunayomi.core.network.await
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads the manga-ocr model on request: an ONNX export of manga-ocr-base-2025 (Apache-2.0),
 * a small ViT encoder and a two-layer BERT decoder, about 140 MB in all.
 */
@Singleton
class MangaOcrModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    internal val dir: File get() = File(context.filesDir, "models/manga_ocr_2025").apply { mkdirs() }

    internal fun file(name: String) = File(dir, name)

    private val _status = MutableStateFlow<MangaOcrStatus>(MangaOcrStatus.NotDownloaded)
    val status: StateFlow<MangaOcrStatus> = _status.asStateFlow()

    /** Bumped when the files change, so the recognizer reloads them. */
    @Volatile
    internal var generation = 0
        private set

    init {
        // The first version of this screen stored a model that never worked; drop it.
        File(context.filesDir, "models/manga_ocr").deleteRecursively()
        refreshStatus()
    }

    fun isReady(): Boolean = FILES.all { (name, minSize) -> file(name).length() >= minSize }

    private fun refreshStatus() {
        _status.value = if (isReady()) {
            MangaOcrStatus.Ready(FILES.sumOf { file(it.first).length() })
        } else {
            MangaOcrStatus.NotDownloaded
        }
    }

    fun download() {
        if (downloadJob?.isActive == true || isReady()) return
        downloadJob = scope.launch {
            try {
                var done = 0L
                _status.value = MangaOcrStatus.Downloading(0f, 0L, TOTAL_BYTES)
                for ((name, _) in FILES) {
                    val target = file(name)
                    if (target.exists()) {
                        done += target.length()
                        continue
                    }
                    val before = done
                    download("$BASE_URL/$name", File(dir, "$name.part")) { read ->
                        done = before + read
                        _status.value = MangaOcrStatus.Downloading((done.toFloat() / TOTAL_BYTES).coerceIn(0f, 1f), done, TOTAL_BYTES)
                    }
                    File(dir, "$name.part").renameTo(target)
                }
                generation++
                refreshStatus()
            } catch (e: CancellationException) {
                refreshStatus()
                throw e
            } catch (e: Exception) {
                _status.value = MangaOcrStatus.Error(e.message ?: e.toString())
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
        downloadJob = null
        dir.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
        refreshStatus()
    }

    fun delete() {
        cancel()
        dir.deleteRecursively()
        generation++
        refreshStatus()
    }

    private suspend fun download(url: String, target: File, onProgress: (Long) -> Unit) {
        val response = client.newCall(Request.Builder().url(url).build()).await()
        response.use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}: $url")
            it.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        scope.ensureActive()
                        output.write(buffer, 0, n)
                        total += n
                        onProgress(total)
                    }
                }
            }
        }
    }

    internal companion object {
        // Pinned to a commit so the files can't change under the app.
        private const val BASE_URL =
            "https://huggingface.co/l0wgear/manga-ocr-2025-onnx/resolve/e8b27bbd3f424fe3877e0bda704d6a920e4f0a33"

        const val ENCODER = "encoder_model.onnx"
        const val DECODER = "decoder_model.onnx"
        const val VOCAB = "vocab.txt"

        /** File and the smallest size a complete copy has. */
        private val FILES = listOf(VOCAB to 20_000L, ENCODER to 20_000_000L, DECODER to 100_000_000L)
        private const val TOTAL_BYTES = 140_434_411L // the three files at that commit
    }
}

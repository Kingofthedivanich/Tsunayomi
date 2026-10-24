package app.tsunayomi.data

import android.content.Context
import app.tsunayomi.core.model.MangaOcrStatus
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MangaOcrModelManagerTest {

    @TempDir
    lateinit var tempDir: File

    private val context: Context = mockk()
    private val client: OkHttpClient = OkHttpClient()

    private lateinit var manager: MangaOcrModelManager

    @BeforeEach
    fun setup() {
        every { context.filesDir } returns tempDir
        manager = MangaOcrModelManager(context, client)
    }

    @Test
    fun `initial status is NotDownloaded when model file is missing`() {
        assertFalse(manager.isReady())
        assertEquals(MangaOcrStatus.NotDownloaded, manager.status.value)
    }

    @Test
    fun `delete removes model files and resets status`() {
        val modelDir = File(tempDir, "models/manga_ocr").apply { mkdirs() }
        val fakeModel = File(modelDir, "manga_ocr.onnx").apply { writeBytes(ByteArray(100)) }

        manager.delete()

        assertFalse(fakeModel.exists())
        assertEquals(MangaOcrStatus.NotDownloaded, manager.status.value)
    }
}

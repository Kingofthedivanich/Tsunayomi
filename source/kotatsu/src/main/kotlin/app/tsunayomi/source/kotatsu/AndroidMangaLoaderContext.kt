package app.tsunayomi.source.kotatsu

import android.content.Context
import android.graphics.BitmapFactory
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.bitmap.Bitmap as KotatsuBitmap
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.io.ByteArrayOutputStream

internal class AndroidMangaLoaderContext(
    private val context: Context,
    baseClient: OkHttpClient,
) : MangaLoaderContext() {

    override val cookieJar: CookieJar = AndroidCookieJar()

    override val httpClient: OkHttpClient = baseClient.newBuilder()
        .cookieJar(cookieJar)
        .addInterceptor { chain ->
            val req = chain.request()
            val newReq = if (req.header("User-Agent") == null) {
                req.newBuilder()
                    .header("User-Agent", getDefaultUserAgent())
                    .build()
            } else {
                req
            }
            chain.proceed(newReq)
        }
        .build()

    override fun getDefaultUserAgent(): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    override fun getConfig(source: MangaSource): MangaSourceConfig {
        val prefs = context.getSharedPreferences(
            "kotatsu_source_${source.name.lowercase()}",
            Context.MODE_PRIVATE,
        )
        return SharedPreferencesMangaSourceConfig(prefs)
    }

    @Deprecated("Deprecated in base class", ReplaceWith("evaluateJs(baseUrl, script)"))
    override suspend fun evaluateJs(script: String): String = withContext(Dispatchers.Default) {
        runCatching {
            QuickJs.create().use { qjs ->
                qjs.evaluate(script)?.toString().orEmpty()
            }
        }.getOrElse { "" }
    }

    override suspend fun evaluateJs(baseUrl: String, script: String): String = evaluateJs(script)

    override fun createBitmap(width: Int, height: Int): KotatsuBitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(
            width,
            height,
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        return AndroidKotatsuBitmap(bitmap)
    }

    override fun redrawImageResponse(
        response: Response,
        redraw: (KotatsuBitmap) -> KotatsuBitmap,
    ): Response {
        val body = response.body
        val originalAndroidBitmap = BitmapFactory.decodeStream(body.byteStream()) ?: return response
        val transformed = redraw(AndroidKotatsuBitmap(originalAndroidBitmap))
        val transformedAndroidBitmap = (transformed as? AndroidKotatsuBitmap)?.androidBitmap ?: originalAndroidBitmap
        val out = ByteArrayOutputStream()
        transformedAndroidBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        val bytes = out.toByteArray()
        return response.newBuilder()
            .body(bytes.toResponseBody("image/png".toMediaType()))
            .build()
    }
}

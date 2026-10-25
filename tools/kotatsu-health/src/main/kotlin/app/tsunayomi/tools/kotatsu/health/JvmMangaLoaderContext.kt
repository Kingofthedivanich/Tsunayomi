package app.tsunayomi.tools.kotatsu.health

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.bitmap.Bitmap as KotatsuBitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect as KotatsuRect
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

internal class JvmMangaLoaderContext : MangaLoaderContext() {

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override val cookieJar: CookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = cookieStore.getOrPut(url.host) { mutableListOf() }
            synchronized(list) {
                list.removeAll { old -> cookies.any { it.name == old.name } }
                list.addAll(cookies)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    override val httpClient: OkHttpClient = OkHttpClient.Builder()
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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    override fun getDefaultUserAgent(): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    private val configs = ConcurrentHashMap<String, MangaSourceConfig>()

    override fun getConfig(source: MangaSource): MangaSourceConfig {
        return configs.getOrPut(source.name) {
            object : MangaSourceConfig {
                override fun <T> get(key: ConfigKey<T>): T = key.defaultValue
            }
        }
    }

    @Deprecated("Deprecated in base class", ReplaceWith("evaluateJs(baseUrl, script)"))
    override suspend fun evaluateJs(script: String): String = ""

    override suspend fun evaluateJs(baseUrl: String, script: String): String = ""

    override fun createBitmap(width: Int, height: Int): KotatsuBitmap {
        return JvmKotatsuBitmap(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB))
    }

    override fun redrawImageResponse(
        response: Response,
        redraw: (KotatsuBitmap) -> KotatsuBitmap,
    ): Response {
        val body = response.body
        val img = ImageIO.read(body.byteStream()) ?: return response
        val transformed = redraw(JvmKotatsuBitmap(img))
        val jvmImg = (transformed as? JvmKotatsuBitmap)?.image ?: img
        val out = ByteArrayOutputStream()
        ImageIO.write(jvmImg, "png", out)
        return response.newBuilder()
            .body(out.toByteArray().toResponseBody("image/png".toMediaType()))
            .build()
    }
}

internal class JvmKotatsuBitmap(val image: BufferedImage) : KotatsuBitmap {
    override val width: Int get() = image.width
    override val height: Int get() = image.height

    override fun drawBitmap(sourceBitmap: KotatsuBitmap, src: KotatsuRect, dst: KotatsuRect) {
        val srcImg = (sourceBitmap as? JvmKotatsuBitmap)?.image ?: return
        val g: Graphics2D = image.createGraphics()
        try {
            g.drawImage(
                srcImg,
                dst.left, dst.top, dst.right, dst.bottom,
                src.left, src.top, src.right, src.bottom,
                null,
            )
        } finally {
            g.dispose()
        }
    }
}

package app.tsunayomi.source.kotatsu

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal object KotatsuUrls {
    const val HOST = "kotatsu.tsunayomi.invalid"

    fun page(sourceId: Long, pageUrl: String, preview: String? = null, pageId: Long = 0L): String = HttpUrl.Builder()
        .scheme("https")
        .host(HOST)
        .addPathSegment("page")
        .addPathSegment(sourceId.toString())
        .addQueryParameter("url", pageUrl)
        .apply {
            if (!preview.isNullOrBlank()) addQueryParameter("preview", preview)
            if (pageId != 0L) addQueryParameter("id", pageId.toString())
        }
        .build()
        .toString()

    fun original(url: String): String {
        val parsed = runCatching { url.toHttpUrl() }.getOrNull()
        return if (parsed?.host == HOST) parsed.queryParameter("url") ?: url else url
    }
}

package app.tsunayomi.feature.reader

import android.content.Context
import app.tsunayomi.source.api.SPage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest

/** Builds the Coil request for a page: local URIs go straight to the fetchers, remote ones keep their headers. */
internal fun SPage.toImageRequest(context: Context, configure: ImageRequest.Builder.() -> Unit = {}): ImageRequest =
    ImageRequest.Builder(context)
        .apply {
            when (val page = this@toImageRequest) {
                is SPage.Local -> data(page.uri)
                is SPage.Remote -> {
                    data(page.imageUrl)
                    if (page.headers.isNotEmpty()) {
                        httpHeaders(
                            NetworkHeaders.Builder().apply { page.headers.forEach { (key, value) -> set(key, value) } }.build(),
                        )
                    }
                }
            }
        }
        .apply(configure)
        .build()

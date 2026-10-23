package app.tsunayomi.core.image

import android.net.Uri

/**
 * URIs for pages that are not plain files: an entry inside an archive or a page of a PDF.
 * Sources build them, [LocalPageFetcher] resolves them. Keep the format stable: it is stored as cover URLs.
 */
sealed interface LocalPageUri {
    /** Image inside a ZIP/CBZ. A null [entry] means "the first image", used for covers. */
    data class ArchiveEntry(val file: Uri, val entry: String?) : LocalPageUri

    data class PdfPage(val file: Uri, val page: Int) : LocalPageUri

    fun toUriString(): String = when (this) {
        is ArchiveEntry -> Uri.Builder().scheme(SCHEME).authority(HOST_ARCHIVE)
            .appendQueryParameter(PARAM_FILE, file.toString())
            .apply { if (entry != null) appendQueryParameter(PARAM_ENTRY, entry) }
            .build().toString()
        is PdfPage -> Uri.Builder().scheme(SCHEME).authority(HOST_PDF)
            .appendQueryParameter(PARAM_FILE, file.toString())
            .appendQueryParameter(PARAM_PAGE, page.toString())
            .build().toString()
    }

    companion object {
        const val SCHEME = "tsunayomi"
        private const val HOST_ARCHIVE = "archive"
        private const val HOST_PDF = "pdf"
        private const val PARAM_FILE = "file"
        private const val PARAM_ENTRY = "entry"
        private const val PARAM_PAGE = "page"

        fun parse(uri: Uri): LocalPageUri? {
            if (uri.scheme != SCHEME) return null
            val file = uri.getQueryParameter(PARAM_FILE)?.let(Uri::parse) ?: return null
            return when (uri.authority) {
                HOST_ARCHIVE -> ArchiveEntry(file, uri.getQueryParameter(PARAM_ENTRY))
                HOST_PDF -> PdfPage(file, uri.getQueryParameter(PARAM_PAGE)?.toIntOrNull() ?: return null)
                else -> null
            }
        }
    }
}

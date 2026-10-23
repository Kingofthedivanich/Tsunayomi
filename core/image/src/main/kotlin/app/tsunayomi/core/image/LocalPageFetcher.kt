package app.tsunayomi.core.image

import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import coil3.toAndroidUri
import okio.Buffer
import javax.inject.Inject

/** Loads [LocalPageUri] pages for Coil: archive entries as encoded bytes, PDF pages as rendered bitmaps. */
internal class LocalPageFetcher(
    private val page: LocalPageUri,
    private val options: Options,
    private val archives: ArchiveReader,
    private val pdfs: PdfPageRenderer,
) : Fetcher {
    override suspend fun fetch(): FetchResult = when (page) {
        is LocalPageUri.ArchiveEntry -> {
            val entry = page.entry ?: archives.imageEntries(page.file).firstOrNull()
                ?: error("No images in ${page.file}")
            val bytes = archives.readEntry(page.file, entry)
            SourceFetchResult(
                source = ImageSource(Buffer().write(bytes), options.fileSystem),
                mimeType = ImageFiles.mimeType(entry),
                dataSource = DataSource.DISK,
            )
        }
        is LocalPageUri.PdfPage -> {
            // Render at twice the requested width so zooming in stays sharp.
            val requested = options.size.width.pxOrElse { DEFAULT_PDF_WIDTH }
            val bitmap = pdfs.render(page.file, page.page, requested * 2)
            ImageFetchResult(image = bitmap.asImage(), isSampled = false, dataSource = DataSource.DISK)
        }
    }

    class Factory @Inject constructor(
        private val archives: ArchiveReader,
        private val pdfs: PdfPageRenderer,
    ) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != LocalPageUri.SCHEME) return null
            val page = LocalPageUri.parse(data.toAndroidUri()) ?: return null
            return LocalPageFetcher(page, options, archives, pdfs)
        }
    }

    private companion object {
        const val DEFAULT_PDF_WIDTH = 1080
    }
}

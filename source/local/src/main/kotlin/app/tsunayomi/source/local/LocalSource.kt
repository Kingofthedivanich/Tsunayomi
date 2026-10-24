package app.tsunayomi.source.local

import android.content.Context
import androidx.core.net.toUri
import app.tsunayomi.core.common.Dispatcher
import app.tsunayomi.core.common.NaturalOrderComparator
import app.tsunayomi.core.common.TsunayomiDispatcher
import app.tsunayomi.core.datastore.UserPreferencesDataSource
import app.tsunayomi.core.image.ArchiveReader
import app.tsunayomi.core.image.EpubMetadata
import app.tsunayomi.core.image.ImageFiles
import app.tsunayomi.core.image.LocalPageUri
import app.tsunayomi.core.image.PdfPageRenderer
import app.tsunayomi.source.api.CatalogSource
import app.tsunayomi.source.api.FilterList
import app.tsunayomi.source.api.MangaPage
import app.tsunayomi.source.api.ReadingDirection
import app.tsunayomi.source.api.SChapter
import app.tsunayomi.source.api.SManga
import app.tsunayomi.source.api.SPage
import app.tsunayomi.source.api.Source
import app.tsunayomi.source.api.SourceIds
import app.tsunayomi.source.api.SourceNotConfiguredException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manga stored on the device, in the folder the user picked:
 *
 * ```
 * <library>/<series>/cover.jpg            optional
 * <library>/<series>/ComicInfo.xml        optional
 * <library>/<series>/<chapter>/001.jpg    a folder of images,
 * <library>/<series>/<chapter>.cbz        a ZIP/CBZ archive,
 * <library>/<series>/<chapter>.pdf        or a PDF.
 * ```
 * Images placed directly in the series folder are read as a single chapter.
 */
@Singleton
class LocalSource @Inject constructor(
    @ApplicationContext context: Context,
    private val preferences: UserPreferencesDataSource,
    private val archives: ArchiveReader,
    private val pdfs: PdfPageRenderer,
    @Dispatcher(TsunayomiDispatcher.IO) private val io: CoroutineDispatcher,
) : CatalogSource {
    override val id: Long = ID
    override val name: String = "Local"
    override val lang: String = Source.MULTI_LANG
    override val supportsLatest: Boolean = true

    private val saf = SafTree(context.contentResolver)

    override suspend fun getPopular(page: Int): MangaPage = listSeries(query = null, newestFirst = false)

    override suspend fun getLatest(page: Int): MangaPage = listSeries(query = null, newestFirst = true)

    override suspend fun search(query: String, filters: FilterList, page: Int): MangaPage =
        listSeries(query = query.trim().takeIf { it.isNotEmpty() }, newestFirst = false)

    override suspend fun getDetails(manga: SManga): SManga = withContext(io) {
        val mangaUri = manga.url.toUri()
        val document = saf.document(mangaUri)
        val children = if (document != null && !document.isDirectory) listOf(document) else saf.children(mangaUri)
        val info = readComicInfo(children)
        val epub = if (info == null) readEpubMetadata(children) else null
        manga.copy(
            title = info?.series ?: epub?.title ?: manga.title,
            coverUrl = manga.coverUrl ?: findCover(children, epub),
            author = info?.writer ?: epub?.author ?: manga.author,
            artist = info?.penciller ?: manga.artist,
            description = info?.summary ?: epub?.description ?: manga.description,
            genres = info?.genres?.takeIf { it.isNotEmpty() } ?: manga.genres,
            status = info?.status?.toStatus() ?: manga.status,
            readingDirection = when {
                info?.rightToLeft == true -> ReadingDirection.RightToLeft
                info?.rightToLeft == false -> ReadingDirection.LeftToRight
                epub?.isRightToLeft == true -> ReadingDirection.RightToLeft
                epub?.isRightToLeft == false -> ReadingDirection.LeftToRight
                else -> manga.readingDirection
            },
            originalLang = info?.languageIso ?: manga.originalLang,
        )
    }

    override suspend fun getChapters(manga: SManga): List<SChapter> = withContext(io) {
        val mangaUri = manga.url.toUri()
        val document = saf.document(mangaUri)
        if (document != null && !document.isDirectory) {
            return@withContext listOf(
                SChapter(
                    url = manga.url,
                    name = manga.title,
                    number = ChapterNumberParser.parse(document.name),
                    uploadedAt = document.lastModified.takeIf { it > 0 },
                )
            )
        }
        val children = saf.children(mangaUri)
        val chapters = children.filter { it.isChapter() }.sortedWith(compareBy(NaturalOrderComparator) { it.name })
        if (chapters.isEmpty() && children.any { ImageFiles.isImage(it.name) }) {
            return@withContext listOf(SChapter(url = manga.url, name = manga.title, number = 1f))
        }
        chapters.map { entry ->
            SChapter(
                url = entry.uri.toString(),
                name = entry.nameWithoutExtension,
                number = ChapterNumberParser.parse(entry.name),
                uploadedAt = entry.lastModified.takeIf { it > 0 },
            )
        }
    }

    override suspend fun getPages(chapter: SChapter): List<SPage> = withContext(io) {
        val uri = chapter.url.toUri()
        val document = saf.document(uri) ?: error("Chapter not found: ${chapter.url}")
        when {
            document.isDirectory -> saf.children(uri)
                .filter { !it.isDirectory && ImageFiles.isImage(it.name) }
                .sortedWith(compareBy(NaturalOrderComparator) { it.name })
                .mapIndexed { index, image -> SPage.Local(index, image.uri.toString()) }
            document.extension in ARCHIVE_EXTENSIONS -> archives.imageEntries(uri)
                .mapIndexed { index, entry ->
                    SPage.Local(index, LocalPageUri.ArchiveEntry(uri, entry).toUriString())
                }
            document.extension == PDF_EXTENSION -> List(pdfs.pageCount(uri)) { index ->
                SPage.Local(index, LocalPageUri.PdfPage(uri, index).toUriString())
            }
            else -> error("Unsupported chapter format: ${document.name}")
        }
    }

    private suspend fun listSeries(query: String?, newestFirst: Boolean): MangaPage = withContext(io) {
        val library = preferences.settings.first().localLibraryUri
            ?: throw SourceNotConfiguredException("Local library folder is not selected")
        val series = saf.children(library.toUri())
            .filter { !it.name.startsWith(".") && (it.isDirectory || it.isChapter()) }
            .filter { query == null || it.name.contains(query, ignoreCase = true) }
            .let { dirs ->
                if (newestFirst) dirs.sortedByDescending { it.lastModified }
                else dirs.sortedWith(compareBy(NaturalOrderComparator) { it.name })
            }
        val limiter = Semaphore(COVER_LOOKUP_PARALLELISM)
        val items = coroutineScope {
            series.map { item ->
                async {
                    val cover = limiter.withPermit {
                        val children = if (item.isDirectory) saf.children(item.uri) else listOf(item)
                        val epub = if (item.extension == EPUB_EXTENSION) {
                            runCatching { archives.readEpubMetadata(item.uri) }.getOrNull()
                        } else null
                        runCatching { findCover(children, epub) }.getOrNull()
                    }
                    val title = if (item.isDirectory) item.name else item.nameWithoutExtension
                    SManga(url = item.uri.toString(), title = title, coverUrl = cover)
                }
            }.awaitAll()
        }
        MangaPage(items, hasNext = false)
    }

    /** cover.* in the series folder, otherwise the first page of the first chapter. */
    private fun findCover(seriesChildren: List<DocumentEntry>, epub: EpubMetadata? = null): String? {
        seriesChildren.firstOrNull { ImageFiles.isImage(it.name) && it.nameWithoutExtension.equals("cover", true) }
            ?.let { return it.uri.toString() }
        val firstEpub = seriesChildren.firstOrNull { it.extension == EPUB_EXTENSION }
        if (firstEpub != null && epub?.coverEntry != null) {
            return LocalPageUri.ArchiveEntry(firstEpub.uri, epub.coverEntry).toUriString()
        }
        val firstChapter = seriesChildren.filter { it.isChapter() }.minWithOrNull(compareBy(NaturalOrderComparator) { it.name })
            ?: return seriesChildren.filter { ImageFiles.isImage(it.name) }
                .minWithOrNull(compareBy(NaturalOrderComparator) { it.name })?.uri?.toString()
        return when {
            firstChapter.isDirectory -> saf.children(firstChapter.uri)
                .filter { ImageFiles.isImage(it.name) }
                .minWithOrNull(compareBy(NaturalOrderComparator) { it.name })
                ?.uri?.toString()
            firstChapter.extension in ARCHIVE_EXTENSIONS ->
                LocalPageUri.ArchiveEntry(firstChapter.uri, entry = null).toUriString()
            else -> LocalPageUri.PdfPage(firstChapter.uri, page = 0).toUriString()
        }
    }

    private suspend fun readComicInfo(seriesChildren: List<DocumentEntry>): ComicInfo? {
        seriesChildren.firstOrNull { it.name.equals(COMIC_INFO, ignoreCase = true) }?.let { file ->
            return saf.readText(file.uri)?.let { ComicInfo.parse(it.byteInputStream()) }
        }
        val firstArchive = seriesChildren.filter { it.extension in ARCHIVE_EXTENSIONS }
            .minWithOrNull(compareBy(NaturalOrderComparator) { it.name }) ?: return null
        return runCatching { archives.readTextOrNull(firstArchive.uri, COMIC_INFO) }.getOrNull()
            ?.let { ComicInfo.parse(it.byteInputStream()) }
    }

    private suspend fun readEpubMetadata(seriesChildren: List<DocumentEntry>): EpubMetadata? {
        val firstEpub = seriesChildren.firstOrNull { it.extension == EPUB_EXTENSION } ?: return null
        return runCatching { archives.readEpubMetadata(firstEpub.uri) }.getOrNull()
    }

    private fun DocumentEntry.isChapter(): Boolean =
        !name.startsWith(".") && (isDirectory || extension in ARCHIVE_EXTENSIONS || extension == PDF_EXTENSION)

    private fun String.toStatus(): SManga.Status = when (lowercase()) {
        "ongoing" -> SManga.Status.Ongoing
        "completed" -> SManga.Status.Completed
        "on hiatus", "hiatus" -> SManga.Status.Hiatus
        "cancelled", "canceled" -> SManga.Status.Cancelled
        else -> SManga.Status.Unknown
    }

    companion object {
        /** Fixed id: local manga must survive any change to the source's name. */
        const val ID = SourceIds.LOCAL

        private const val COMIC_INFO = "ComicInfo.xml"
        private const val PDF_EXTENSION = "pdf"
        private const val EPUB_EXTENSION = "epub"
        private val ARCHIVE_EXTENSIONS = setOf("cbz", "zip", "cbr", "rar", "epub")
        private const val COVER_LOOKUP_PARALLELISM = 8
    }
}

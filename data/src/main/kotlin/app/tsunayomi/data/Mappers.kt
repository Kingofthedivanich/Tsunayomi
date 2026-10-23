package app.tsunayomi.data

import app.tsunayomi.core.database.entity.ChapterEntity
import app.tsunayomi.core.database.entity.MangaEntity
import app.tsunayomi.core.model.Chapter
import app.tsunayomi.core.model.Manga
import app.tsunayomi.core.model.MangaStatus
import app.tsunayomi.core.model.ReaderMode
import app.tsunayomi.source.api.ReadingDirection
import app.tsunayomi.source.api.SChapter
import app.tsunayomi.source.api.SManga

internal fun MangaEntity.toModel() = Manga(
    id = id,
    sourceId = sourceId,
    url = url,
    title = title,
    coverUrl = coverUrl,
    author = author,
    artist = artist,
    description = description,
    genres = genres,
    status = status,
    favorite = favorite,
    dateAdded = dateAdded,
    readerMode = readerMode,
    originalLang = originalLang,
)

internal fun Manga.toSManga() = SManga(
    url = url,
    title = title,
    coverUrl = coverUrl,
    author = author,
    artist = artist,
    description = description,
    genres = genres,
    originalLang = originalLang,
)

internal fun ChapterEntity.toModel() = Chapter(
    id = id,
    mangaId = mangaId,
    url = url,
    name = name,
    number = number,
    scanlator = scanlator,
    uploadedAt = uploadedAt,
    read = read,
    lastPageRead = lastPageRead,
    bookmark = bookmark,
    sourceOrder = sourceOrder,
)

internal fun Chapter.toSChapter() = SChapter(
    url = url,
    name = name,
    number = number,
    uploadedAt = uploadedAt,
    scanlator = scanlator,
)

internal fun SChapter.toEntity(mangaId: Long, sourceOrder: Int) = ChapterEntity(
    mangaId = mangaId,
    url = url,
    name = name,
    number = number,
    scanlator = scanlator,
    uploadedAt = uploadedAt,
    read = false,
    lastPageRead = 0,
    bookmark = false,
    sourceOrder = sourceOrder,
)

internal fun SManga.Status.toModel(): MangaStatus = when (this) {
    SManga.Status.Unknown -> MangaStatus.Unknown
    SManga.Status.Ongoing -> MangaStatus.Ongoing
    SManga.Status.Completed -> MangaStatus.Completed
    SManga.Status.Hiatus -> MangaStatus.Hiatus
    SManga.Status.Cancelled -> MangaStatus.Cancelled
}

internal fun ReadingDirection.toReaderMode(): ReaderMode = when (this) {
    ReadingDirection.LeftToRight -> ReaderMode.PagedLeftToRight
    ReadingDirection.RightToLeft -> ReaderMode.PagedRightToLeft
    ReadingDirection.Vertical -> ReaderMode.Vertical
    ReadingDirection.Webtoon -> ReaderMode.Webtoon
}

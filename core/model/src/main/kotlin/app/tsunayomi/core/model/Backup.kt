package app.tsunayomi.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupRoot(
    val version: Int = CURRENT_VERSION,
    val createdAt: Long = System.currentTimeMillis(),
    val appVersion: String = "",
    val categories: List<BackupCategory> = emptyList(),
    val manga: List<BackupManga> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class BackupCategory(
    val name: String,
    val order: Int = 0,
)

@Serializable
data class BackupManga(
    val sourceId: Long,
    val url: String,
    val title: String,
    val coverUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.Unknown,
    val favorite: Boolean = true,
    val dateAdded: Long = 0L,
    val readerMode: ReaderMode? = null,
    val originalLang: String? = null,
    val detailsUpdatedAt: Long = 0L,
    val categories: List<String> = emptyList(),
    val chapters: List<BackupChapter> = emptyList(),
    val history: List<BackupHistory> = emptyList(),
    val glossary: List<BackupGlossaryTerm> = emptyList(),
    val translatePages: Boolean? = null,
)

@Serializable
data class BackupChapter(
    val url: String,
    val name: String,
    val number: Float = 0f,
    val scanlator: String? = null,
    val uploadedAt: Long? = null,
    val read: Boolean = false,
    val lastPageRead: Int = 0,
    val bookmark: Boolean = false,
    val sourceOrder: Int = 0,
    val language: String? = null,
)

@Serializable
data class BackupHistory(
    val chapterUrl: String,
    val lastReadAt: Long,
    val timeRead: Long = 0L,
)

@Serializable
data class BackupGlossaryTerm(
    val targetLang: String,
    val source: String,
    val translation: String,
)

sealed interface BackupResult {
    data class Success(val mangaCount: Int, val categoryCount: Int, val chapterCount: Int) : BackupResult
    data class Error(val throwable: Throwable) : BackupResult
}

sealed interface RestoreResult {
    data class Success(val mangaCount: Int, val categoryCount: Int, val chapterCount: Int) : RestoreResult
    data class Error(val throwable: Throwable) : RestoreResult
}

data class RestoreProgress(
    val current: Int,
    val total: Int,
    val currentTitle: String,
)

package app.tsunayomi.core.model

data class Chapter(
    val id: Long,
    val mangaId: Long,
    val url: String,
    val name: String,
    /** Parsed chapter number, or a negative value if unknown. */
    val number: Float,
    val scanlator: String? = null,
    val uploadedAt: Long? = null,
    val read: Boolean = false,
    val lastPageRead: Int = 0,
    val bookmark: Boolean = false,
    val sourceOrder: Int = 0,
)

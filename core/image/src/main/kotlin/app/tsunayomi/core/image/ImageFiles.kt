package app.tsunayomi.core.image

object ImageFiles {
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp", "heic", "heif")

    fun isImage(name: String): Boolean = extension(name) in IMAGE_EXTENSIONS

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun mimeType(name: String): String? = when (extension(name)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "avif" -> "image/avif"
        "bmp" -> "image/bmp"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        else -> null
    }
}

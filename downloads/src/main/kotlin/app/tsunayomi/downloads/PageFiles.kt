package app.tsunayomi.downloads

/** Naming of pages inside a downloaded CBZ; zero-padded so any reader sorts them right. */
internal object PageFiles {
    fun entryName(index: Int, extension: String): String = "%04d.%s".format(index + 1, extension)

    /** File extension from the response's content type, falling back to the URL. */
    fun extension(contentType: String?, url: String): String {
        when (contentType?.substringBefore(';')?.trim()?.lowercase()) {
            "image/jpeg", "image/jpg" -> return "jpg"
            "image/png" -> return "png"
            "image/webp" -> return "webp"
            "image/gif" -> return "gif"
            "image/avif" -> return "avif"
        }
        val fromUrl = url.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return fromUrl.takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif", "avif") } ?: "jpg"
    }
}

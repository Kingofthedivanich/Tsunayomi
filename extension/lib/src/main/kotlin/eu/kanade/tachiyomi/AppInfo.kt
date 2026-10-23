package eu.kanade.tachiyomi

/**
 * Used by extensions.
 */
@Suppress("UNUSED")
object AppInfo {
    private var versionCode = 0
    private var versionName = ""

    /** Called once by the host app; this library can't see the app's BuildConfig. */
    fun init(versionCode: Int, versionName: String) {
        this.versionCode = versionCode
        this.versionName = versionName
    }

    /**
     * Version code of the host application. May be useful for sharing as User-Agent information.
     * Note that this value differs between forks so logic should not rely on it.
     *
     * @since extension-lib 1.3
     */
    fun getVersionCode(): Int = versionCode

    /**
     * Version name of the host application. May be useful for sharing as User-Agent information.
     * Note that this value differs between forks so logic should not rely on it.
     *
     * @since extension-lib 1.3
     */
    fun getVersionName(): String = versionName

    /**
     * A list of supported image MIME types by the reader.
     *
     * @since extension-lib 1.5
     */
    fun getSupportedImageMimeTypes(): List<String> =
        listOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/avif", "image/heif", "image/jxl")
}

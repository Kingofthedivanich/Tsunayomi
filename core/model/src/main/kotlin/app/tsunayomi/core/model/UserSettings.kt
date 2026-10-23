package app.tsunayomi.core.model

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    /** SAF tree URI of the local library folder, or null if the user has not picked one. */
    val localLibraryUri: String? = null,
    val defaultReaderMode: ReaderMode = ReaderMode.PagedRightToLeft,
    val volumeKeysNavigation: Boolean = true,
)

enum class ThemeMode {
    System,
    Light,
    Dark,
}

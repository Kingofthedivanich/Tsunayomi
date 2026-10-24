package app.tsunayomi.core.model

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    /** SAF tree URI of the local library folder, or null if the user has not picked one. */
    val localLibraryUri: String? = null,
    val defaultReaderMode: ReaderMode = ReaderMode.PagedRightToLeft,
    val volumeKeysNavigation: Boolean = true,
    val librarySort: LibrarySort = LibrarySort.Title,
    val librarySortAscending: Boolean = true,
    val translationEngine: TranslationEngine = TranslationEngine.MlKit,
    /** ISO 639-1 target language; empty means "the app's language". */
    val translationTargetLang: String = "",
    val translateByDefault: Boolean = false,
    /** Translate chapters right after downloading, so offline reading shows translations instantly. */
    val translateOnDownload: Boolean = true,
    /** Hours between background library updates; 0 turns them off. */
    val libraryUpdateIntervalHours: Int = 12,
    val libraryUpdateWifiOnly: Boolean = false,
    /** Queue chapters found by a library update for download (and translation, if that is on). */
    val autoDownloadNewChapters: Boolean = false,
    /** When enabled, reading progress and history are not saved. */
    val incognitoMode: Boolean = false,
    /** Require biometric authentication or device credential to open the app. */
    val appLockEnabled: Boolean = false,
    /** Inactivity timeout in minutes before requiring authentication again (0 = immediately). */
    val appLockTimeoutMinutes: Int = 0,
    /** Block screenshots and hide preview in recent apps switcher. */
    val secureScreen: Boolean = false,
    /** Whether the user completed or skipped the first-launch welcome/setup wizard. */
    val onboardingCompleted: Boolean = false,
)

enum class ThemeMode {
    System,
    Light,
    Dark,
}

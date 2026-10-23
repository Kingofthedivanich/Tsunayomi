package app.tsunayomi.core.model

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
)

enum class ThemeMode {
    System,
    Light,
    Dark,
}

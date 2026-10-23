package app.tsunayomi.core.model

/** Connection to a self-hosted Komga server. An API key, when set, is used instead of the password. */
data class KomgaConfig(
    val url: String,
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
) {
    val hasCredentials: Boolean get() = apiKey.isNotBlank() || (username.isNotBlank() && password.isNotEmpty())
}

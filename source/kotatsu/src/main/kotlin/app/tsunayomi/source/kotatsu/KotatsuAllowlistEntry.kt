package app.tsunayomi.source.kotatsu

import kotlinx.serialization.Serializable

@Serializable
data class KotatsuAllowlistEntry(
    val name: String,
    val locale: String,
    val domain: String,
    val verifiedAt: String,
    val isNsfw: Boolean = false,
)

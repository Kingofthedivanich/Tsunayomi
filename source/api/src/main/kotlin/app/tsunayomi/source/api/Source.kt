package app.tsunayomi.source.api

import java.security.MessageDigest

/** A place manga come from: the local library, a self-hosted server or an online catalog. */
interface Source {
    /** Stable identifier persisted in the database. Never change it for an existing source. */
    val id: Long

    val name: String

    /** ISO 639-1 code of the content language, or [MULTI_LANG] if the source mixes languages. */
    val lang: String

    companion object {
        const val MULTI_LANG = "multi"
    }
}

/**
 * Derives a stable [Source.id] from the source's identity, so the id survives app updates
 * as long as the inputs stay the same. Bump [versionId] only to deliberately orphan old entries.
 */
fun sourceIdOf(name: String, lang: String, versionId: Int = 1): Long {
    val key = "${name.lowercase()}/$lang/$versionId"
    val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
    return (0..7).fold(0L) { acc, i -> acc or ((bytes[i].toLong() and 0xFF) shl (8 * (7 - i))) } and Long.MAX_VALUE
}

object SourceIds {
    /** The on-device library. Fixed so local entries survive renames of the source. */
    const val LOCAL = 0L
}

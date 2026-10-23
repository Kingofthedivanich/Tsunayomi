package app.tsunayomi.extension.manager

import kotlinx.serialization.Serializable

/** An extension repository the user added. Extensions from it are trusted if signed with [signingKey]. */
@Serializable
data class ExtensionRepo(
    /** The address the index was fetched from; identifies the repo. */
    val indexUrl: String,
    val name: String,
    /** SHA-256 of the certificate every extension in this repo is signed with. */
    val signingKey: String,
    val website: String? = null,
    /** Old-style repo: index.min.json next to repo.json, apk/ and icon/ folders. */
    val legacy: Boolean = false,
    /** Newer repos may keep the extension list in a separate file. */
    val extensionListUrl: String? = null,
)

data class AvailableExtension(
    val name: String,
    val pkgName: String,
    val apkUrl: String,
    val iconUrl: String,
    val libVersion: Double,
    val versionCode: Long,
    val versionName: String,
    /** A language code, or "all" for extensions with sources in several languages. */
    val lang: String,
    val nsfw: Boolean,
    val sourceLangs: Set<String>,
    val repo: ExtensionRepo,
)

sealed interface InstalledExtension {
    val pkgName: String
    val name: String
    val versionName: String
    val versionCode: Long
    val iconUri: String?

    data class Loaded(
        override val pkgName: String,
        override val name: String,
        override val versionName: String,
        override val versionCode: Long,
        override val iconUri: String?,
        val lang: String,
        val nsfw: Boolean,
        val sources: List<eu.kanade.tachiyomi.source.Source>,
    ) : InstalledExtension

    data class Failed(
        override val pkgName: String,
        override val name: String,
        override val versionName: String,
        override val versionCode: Long,
        override val iconUri: String?,
        val reason: Reason,
        val detail: String? = null,
    ) : InstalledExtension {
        enum class Reason { UnsupportedLib, Untrusted, Broken }
    }
}

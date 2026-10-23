package app.tsunayomi.extension.manager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// Wire formats of extension repositories, as defined by Mihon.

/** repo.json of an old-style repository; [indexV2] points to the newer index when the repo has one. */
@Serializable
internal data class LegacyRepo(
    @SerialName("index_v2") val indexV2: String? = null,
    val meta: Meta,
) {
    @Serializable
    data class Meta(
        val name: String,
        val shortName: String? = null,
        val website: String,
        val signingKeyFingerprint: String,
    )
}

/** An entry of index.min.json. */
@Serializable
internal data class LegacyExtension(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<Source>? = null,
) {
    @Serializable
    data class Source(val id: Long, val lang: String, val name: String, val baseUrl: String)
}

/** index.pb (or its JSON form) of a newer repository. */
@Serializable
internal data class StoreIndex(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val badgeLabel: String = "",
    @ProtoNumber(3) val signingKey: String,
    @ProtoNumber(4) val contact: Contact? = null,
    @ProtoNumber(101) val extensionList: ExtensionList? = null,
    @ProtoNumber(102) val extensionListUrl: String? = null,
) {
    @Serializable
    data class Contact(@ProtoNumber(1) val website: String = "", @ProtoNumber(2) val discord: String? = null)

    @Serializable
    data class ExtensionList(@ProtoNumber(1) val extensions: List<Extension> = emptyList())

    @Serializable
    data class Extension(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val packageName: String,
        @ProtoNumber(3) val resources: Resources,
        @ProtoNumber(4) val extensionLib: String,
        @ProtoNumber(5) val versionCode: Long,
        @ProtoNumber(6) val versionName: String,
        @ProtoNumber(7) val contentWarning: Int = 0,
        @ProtoNumber(8) val sources: List<Source> = emptyList(),
    )

    @Serializable
    data class Resources(@ProtoNumber(1) val apkUrl: String, @ProtoNumber(2) val iconUrl: String = "")

    @Serializable
    data class Source(
        @ProtoNumber(1) val id: Long,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val language: String,
        @ProtoNumber(4) val homeUrl: String = "",
    )

    companion object {
        /** Values of the ContentWarning enum: 0 unspecified, 1 safe, 2 mixed, 3 nsfw. */
        const val CONTENT_WARNING_NSFW = 3
    }
}

internal fun LegacyExtension.toAvailable(repo: ExtensionRepo, baseUrl: String): AvailableExtension {
    val langs = sources?.map { it.lang }?.toSet().orEmpty().ifEmpty { setOf(lang) }
    return AvailableExtension(
        name = name.substringAfter("Tachiyomi: "),
        pkgName = pkg,
        apkUrl = "$baseUrl/apk/$apk",
        iconUrl = "$baseUrl/icon/$pkg.png",
        libVersion = version.substringBeforeLast('.').toDoubleOrNull() ?: 0.0,
        versionCode = code,
        versionName = version,
        lang = lang,
        nsfw = nsfw == 1,
        sourceLangs = langs,
        repo = repo,
    )
}

internal fun StoreIndex.Extension.toAvailable(repo: ExtensionRepo): AvailableExtension {
    val langs = sources.map { it.language }.toSet()
    return AvailableExtension(
        name = name,
        pkgName = packageName,
        apkUrl = resources.apkUrl,
        iconUrl = resources.iconUrl,
        libVersion = extensionLib.toDoubleOrNull() ?: 0.0,
        versionCode = versionCode,
        versionName = versionName,
        lang = if (langs.size == 1) langs.first() else "all",
        nsfw = contentWarning == StoreIndex.CONTENT_WARNING_NSFW,
        sourceLangs = langs,
        repo = repo,
    )
}

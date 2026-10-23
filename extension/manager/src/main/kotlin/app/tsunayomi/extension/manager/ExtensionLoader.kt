package app.tsunayomi.extension.manager

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.Collections
import java.util.Enumeration

/**
 * Loads Tachiyomi/Mihon extension APKs kept in the app's private storage. Extensions are not installed into the
 * system, so no install permission is needed and other apps don't see them. Adapted from Mihon's ExtensionLoader.
 */
internal class ExtensionLoader(private val context: Context) {
    val dir = File(context.filesDir, "extensions").apply { mkdirs() }
    private val iconDir = File(context.filesDir, "extension_icons").apply { mkdirs() }

    fun apkFile(pkgName: String) = File(dir, "$pkgName.$APK_SUFFIX")

    fun iconFile(pkgName: String) = File(iconDir, "$pkgName.png")

    fun packageInfo(apk: File): PackageInfo? =
        context.packageManager.getPackageArchiveInfo(apk.absolutePath, PACKAGE_FLAGS)?.also {
            it.applicationInfo?.apply {
                sourceDir = sourceDir ?: apk.absolutePath
                publicSourceDir = publicSourceDir ?: apk.absolutePath
            }
        }

    fun isExtension(info: PackageInfo): Boolean = info.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }

    fun installedApks(): List<File> = dir.listFiles { file -> file.isFile && file.extension == APK_SUFFIX }.orEmpty().toList()

    fun load(apk: File, isTrusted: (signatures: List<String>) -> Boolean): InstalledExtension {
        // Android 14+ refuses to load writable dex files.
        if (apk.canWrite()) apk.setReadOnly()
        val info = packageInfo(apk) ?: return InstalledExtension.Failed(
            pkgName = apk.nameWithoutExtension,
            name = apk.nameWithoutExtension,
            versionName = "",
            versionCode = 0,
            iconUri = null,
            reason = InstalledExtension.Failed.Reason.Broken,
        )
        val appInfo = info.applicationInfo
        val metaData = appInfo?.metaData
        val pkgName = info.packageName
        val name = metaData?.getString(METADATA_NAME)
            ?: appInfo?.let { context.packageManager.getApplicationLabel(it).toString().substringAfter("Tachiyomi: ") }
            ?: pkgName
        val versionName = info.versionName.orEmpty()
        val versionCode = PackageInfoCompat.getLongVersionCode(info)
        val icon = iconFile(pkgName).takeIf { it.isFile }?.toURI()?.toString()
        fun failed(reason: InstalledExtension.Failed.Reason, detail: String? = null) =
            InstalledExtension.Failed(pkgName, name, versionName, versionCode, icon, reason, detail)

        if (!isExtension(info) || metaData == null || versionName.isEmpty()) {
            return failed(InstalledExtension.Failed.Reason.Broken)
        }
        val libVersion = metaData.getFloat(METADATA_EXTENSION_LIB).takeUnless { it == 0f }?.toString()?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion !in SUPPORTED_LIB_VERSIONS) {
            return failed(InstalledExtension.Failed.Reason.UnsupportedLib, libVersion?.toString())
        }
        val signatures = signatures(info)
        if (signatures.isEmpty() || !isTrusted(signatures)) {
            return failed(InstalledExtension.Failed.Reason.Untrusted)
        }
        val nsfw = when {
            metaData.containsKey(METADATA_CONTENT_WARNING) -> metaData.getInt(METADATA_CONTENT_WARNING) == 2
            else -> metaData.getInt(METADATA_NSFW) == 1
        }

        return try {
            val classLoader = DelegateLastPathClassLoader(appInfo.sourceDir, null, context.classLoader)
            val sources = metaData.getString(METADATA_SOURCE_CLASS).orEmpty()
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { if (it.startsWith(".")) pkgName + it else it }
                .flatMap { className ->
                    when (val obj = Class.forName(className, false, classLoader).getDeclaredConstructor().newInstance()) {
                        is Source -> listOf(obj)
                        is SourceFactory -> obj.createSources()
                        else -> error("Unknown source class type: ${obj.javaClass}")
                    }
                }
            if (sources.isEmpty()) return failed(InstalledExtension.Failed.Reason.Broken)
            val langs = sources.map { it.lang }.toSet()
            InstalledExtension.Loaded(
                pkgName = pkgName,
                name = name,
                versionName = versionName,
                versionCode = versionCode,
                iconUri = icon,
                lang = langs.singleOrNull() ?: "all",
                nsfw = nsfw,
                sources = sources,
            )
        } catch (e: Throwable) {
            val root = generateSequence(e) { it.cause }.last()
            failed(InstalledExtension.Failed.Reason.Broken, listOfNotNull(root::class.simpleName, root.message).joinToString(": "))
        }
    }

    /** SHA-256 of each signing certificate, as repositories publish them. */
    fun signatures(info: PackageInfo): List<String> {
        val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptyList()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return certs.orEmpty().map { cert ->
            MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    companion object {
        private const val APK_SUFFIX = "ext"
        private const val EXTENSION_FEATURE = "tachiyomi.extension"
        private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
        private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
        private const val METADATA_NAME = "tachiyomix.name"
        private const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
        private const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"

        val SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.5, 1.6)

        @SuppressLint("InlinedApi")
        @Suppress("DEPRECATION")
        private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)
    }
}

/**
 * Looks classes up in the boot class loader, then the extension's own dex, then the app, so an extension that
 * bundles its own copy of a library uses that copy. From Mihon (DelegateLastClassLoaderCompat).
 */
private class DelegateLastPathClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : PathClassLoader(dexPath, librarySearchPath, parent) {
    private val bootClassLoader: ClassLoader? = Any::class.java.classLoader

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        findLoadedClass(name)?.let { return it }
        if (bootClassLoader != null) {
            try {
                return bootClassLoader.loadClass(name)
            } catch (_: ClassNotFoundException) {
            }
        }
        val fromSelf = try {
            return findClass(name)
        } catch (e: ClassNotFoundException) {
            e
        }
        return try {
            parent.loadClass(name)
        } catch (_: ClassNotFoundException) {
            throw fromSelf
        }
    }

    override fun getResource(name: String?): URL? =
        bootClassLoader?.getResource(name) ?: findResource(name) ?: parent?.getResource(name)

    override fun getResources(name: String?): Enumeration<URL> = Collections.enumeration(
        buildList {
            bootClassLoader?.getResources(name)?.let { addAll(it.toList()) }
            findResources(name)?.let { addAll(it.toList()) }
            parent?.getResources(name)?.let { addAll(it.toList()) }
        },
    )
}

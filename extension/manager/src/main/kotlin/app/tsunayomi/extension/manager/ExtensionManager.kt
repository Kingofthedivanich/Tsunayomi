package app.tsunayomi.extension.manager

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import app.tsunayomi.core.common.ApplicationScope
import app.tsunayomi.source.api.DynamicSources
import app.tsunayomi.source.api.Source
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import javax.inject.Inject
import javax.inject.Singleton

/** Extension repositories, the extensions installed from them and the sources those provide. */
@Singleton
class ExtensionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val repoService: RepoService,
) : DynamicSources {
    private val loader = ExtensionLoader(context)
    private val prefs = context.getSharedPreferences("extensions", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val installMutex = Mutex()
    private val loaded = CountDownLatch(1)

    private val _repos = MutableStateFlow(readRepos())
    val repos: StateFlow<List<ExtensionRepo>> = _repos.asStateFlow()

    private val _installed = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val installed: StateFlow<List<InstalledExtension>> = _installed.asStateFlow()

    private val _available = MutableStateFlow<List<AvailableExtension>>(emptyList())
    val available: StateFlow<List<AvailableExtension>> = _available.asStateFlow()

    private val _languages = MutableStateFlow(prefs.getStringSet(KEY_LANGUAGES, null)?.toSet() ?: DEFAULT_LANGUAGES)

    /** Languages the user reads: filters the extension list and which sources are offered in Browse. */
    val languages: StateFlow<Set<String>> = _languages.asStateFlow()

    private val _showNsfw = MutableStateFlow(prefs.getBoolean(KEY_SHOW_NSFW, false))
    val showNsfw: StateFlow<Boolean> = _showNsfw.asStateFlow()

    private val catalogSources: StateFlow<List<ExtensionCatalogSource>> = _installed
        .map { list ->
            list.filterIsInstance<InstalledExtension.Loaded>().flatMap { extension ->
                extension.sources.filterIsInstance<CatalogueSource>().map { ExtensionCatalogSource(it, extension.iconUri) }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val sources: StateFlow<List<Source>> = catalogSources

    override val browsable: StateFlow<List<Source>> = combine(catalogSources, _languages) { sources, languages ->
        sources.filter { it.lang in languages || it.lang.substringBefore('-') in languages }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        AppInfo.init(packageInfo.versionCode, packageInfo.versionName.orEmpty())
        Injekt = InjektScope(
            ExtensionInjektRegistrar(context as Application, NetworkHelper(context, scope), JavaScriptEngine(context)),
        )
        scope.launch(Dispatchers.IO) {
            try {
                val trustedKeys = _repos.value.map { it.signingKey }.toSet()
                _installed.value = loader.installedApks()
                    .map { apk -> async { loader.load(apk) { signatures -> signatures.any(trustedKeys::contains) } } }
                    .awaitAll()
                    .sortedBy { it.name.lowercase() }
            } finally {
                loaded.countDown()
            }
        }
    }

    override fun awaitLoaded() = loaded.await()

    internal fun catalogSource(id: Long): ExtensionCatalogSource? {
        awaitLoaded()
        return catalogSources.value.firstOrNull { it.id == id }
    }

    /** Settings screens of configurable extension sources work with the extension's own object. */
    fun extensionSource(id: Long): eu.kanade.tachiyomi.source.Source? = catalogSource(id)?.delegate

    suspend fun addRepo(url: String): ExtensionRepo {
        val repo = repoService.fetchRepo(url)
        _repos.update { repos -> repos.filterNot { it.indexUrl == repo.indexUrl } + repo }
        saveRepos()
        reloadAll()
        refreshAvailable()
        return repo
    }

    suspend fun removeRepo(repo: ExtensionRepo) {
        _repos.update { repos -> repos.filterNot { it.indexUrl == repo.indexUrl } }
        saveRepos()
        _available.update { list -> list.filterNot { it.repo.indexUrl == repo.indexUrl } }
        // Extensions signed by that repo's key are no longer trusted.
        reloadAll()
    }

    /** Fetches every repository's extension list. Returns the repos that failed. */
    suspend fun refreshAvailable(): List<Pair<ExtensionRepo, Exception>> = withContext(Dispatchers.IO) {
        val results = _repos.value.map { repo ->
            async { repo to runCatching { repoService.fetchExtensions(repo) } }
        }.awaitAll()
        _available.value = results.flatMap { it.second.getOrDefault(emptyList()) }
            .filter { it.libVersion in ExtensionLoader.SUPPORTED_LIB_VERSIONS }
            .groupBy { it.pkgName }
            .map { (_, versions) -> versions.maxBy { it.versionCode } }
            .sortedBy { it.name.lowercase() }
        results.mapNotNull { (repo, result) -> result.exceptionOrNull()?.let { repo to (it as? Exception ?: Exception(it)) } }
    }

    /** Downloads the APK, checks it is signed by its repository and loads it. Also used to update. */
    suspend fun install(extension: AvailableExtension) = installMutex.withLock {
        withContext(Dispatchers.IO) {
            val temp = File(context.cacheDir, "${extension.pkgName}.download")
            try {
                temp.writeBytes(repoService.download(extension.apkUrl))
                val info = loader.packageInfo(temp) ?: throw IOException("Not an APK: ${extension.apkUrl}")
                check(loader.isExtension(info) && info.packageName == extension.pkgName) {
                    "${extension.apkUrl} is not the ${extension.name} extension"
                }
                check(extension.repo.signingKey in loader.signatures(info)) {
                    "${extension.name} is not signed by ${extension.repo.name}"
                }
                val target = loader.apkFile(extension.pkgName)
                target.delete()
                temp.copyTo(target)
                target.setReadOnly()
                runCatching { loader.iconFile(extension.pkgName).writeBytes(repoService.download(extension.iconUrl)) }
                val result = loader.load(target, ::isTrusted)
                _installed.update { list ->
                    (list.filterNot { it.pkgName == extension.pkgName } + result).sortedBy { it.name.lowercase() }
                }
                if (result is InstalledExtension.Failed) {
                    throw IOException("${extension.name}: ${result.reason}${result.detail?.let { " ($it)" }.orEmpty()}")
                }
            } finally {
                temp.delete()
            }
        }
    }

    suspend fun uninstall(pkgName: String) = installMutex.withLock {
        withContext(Dispatchers.IO) {
            loader.apkFile(pkgName).delete()
            loader.iconFile(pkgName).delete()
                        _installed.update { list -> list.filterNot { it.pkgName == pkgName } }
        }
    }

    fun setLanguageEnabled(lang: String, enabled: Boolean) {
        _languages.update { if (enabled) it + lang else it - lang }
        prefs.edit { putStringSet(KEY_LANGUAGES, _languages.value) }
    }

    fun setShowNsfw(show: Boolean) {
        _showNsfw.value = show
        prefs.edit { putBoolean(KEY_SHOW_NSFW, show) }
    }

    private fun isTrusted(signatures: List<String>) = _repos.value.any { it.signingKey in signatures }

    private suspend fun reloadAll() = installMutex.withLock {
        withContext(Dispatchers.IO) {
            val current = _installed.value.associateBy { it.pkgName }
            _installed.value = loader.installedApks().map { apk ->
                async {
                    val previous = current[apk.nameWithoutExtension]
                    // Keep already loaded extensions as they are, so their sources stay the same objects.
                    if (previous is InstalledExtension.Loaded && isTrusted(loader.signatures(loader.packageInfo(apk) ?: return@async previous))) {
                        previous
                    } else {
                        loader.load(apk, ::isTrusted)
                    }
                }
            }.awaitAll().sortedBy { it.name.lowercase() }
        }
    }

    private fun readRepos(): List<ExtensionRepo> =
        prefs.getStringSet(KEY_REPOS, null).orEmpty().mapNotNull { runCatching { json.decodeFromString<ExtensionRepo>(it) }.getOrNull() }
            .sortedBy { it.name.lowercase() }

    private fun saveRepos() = prefs.edit { putStringSet(KEY_REPOS, _repos.value.map { json.encodeToString(it) }.toSet()) }

    private companion object {
        const val KEY_REPOS = "repos"
        const val KEY_LANGUAGES = "languages"
        const val KEY_SHOW_NSFW = "show_nsfw"

        /** The app's test languages plus multi-language sources; the user can change them. */
        val DEFAULT_LANGUAGES = setOf("all", "multi", "en", "ja", "ko", "ru")
    }
}

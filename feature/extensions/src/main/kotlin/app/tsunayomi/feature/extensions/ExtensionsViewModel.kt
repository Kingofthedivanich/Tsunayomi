package app.tsunayomi.feature.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tsunayomi.extension.manager.AvailableExtension
import app.tsunayomi.extension.manager.ExtensionManager
import app.tsunayomi.extension.manager.ExtensionRepo
import app.tsunayomi.extension.manager.InstalledExtension
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class ExtensionRow(
    val pkgName: String,
    val name: String,
    val version: String,
    val lang: String,
    val iconUri: String?,
    val nsfw: Boolean,
    val installed: InstalledExtension?,
    /** What the repository offers: a newer version when [installed] is set. */
    val available: AvailableExtension?,
)

internal data class ExtensionsUiState(
    val repos: List<ExtensionRepo> = emptyList(),
    val updates: List<ExtensionRow> = emptyList(),
    val installed: List<ExtensionRow> = emptyList(),
    val available: List<ExtensionRow> = emptyList(),
    val query: String = "",
    val languages: Set<String> = emptySet(),
    /** Every language some extension offers, for the language picker. */
    val allLanguages: List<String> = emptyList(),
    val showNsfw: Boolean = false,
    val refreshing: Boolean = false,
    val busy: Set<String> = emptySet(),
)

@HiltViewModel
internal class ExtensionsViewModel @Inject constructor(private val manager: ExtensionManager) : ViewModel() {
    private val query = MutableStateFlow("")
    private val refreshing = MutableStateFlow(false)
    private val busy = MutableStateFlow(emptySet<String>())

    private val messages = Channel<String>(Channel.BUFFERED)
    val messageEvents = messages.receiveAsFlow()

    private val lists = combine(manager.installed, manager.available, manager.languages, manager.showNsfw, query) {
            installed, available, languages, showNsfw, query ->
        val availableByPkg = available.associateBy { it.pkgName }
        val installedPkgs = installed.map { it.pkgName }.toSet()
        val installedRows = installed.map { extension ->
            val offer = availableByPkg[extension.pkgName]
            ExtensionRow(
                pkgName = extension.pkgName,
                name = extension.name,
                version = extension.versionName,
                lang = (extension as? InstalledExtension.Loaded)?.lang ?: offer?.lang.orEmpty(),
                iconUri = extension.iconUri ?: offer?.iconUrl,
                nsfw = (extension as? InstalledExtension.Loaded)?.nsfw ?: offer?.nsfw ?: false,
                installed = extension,
                available = offer?.takeIf { it.versionCode > extension.versionCode },
            )
        }
        val availableRows = available
            .filter { it.pkgName !in installedPkgs }
            .filter { showNsfw || !it.nsfw }
            .filter { extension -> extension.sourceLangs.any { it in languages || it.substringBefore('-') in languages } }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .map { ExtensionRow(it.pkgName, it.name, it.versionName, it.lang, it.iconUrl, it.nsfw, null, it) }
        Triple(installedRows, availableRows, available.flatMap { it.sourceLangs }.toSortedSet().toList())
    }

    val state: StateFlow<ExtensionsUiState> = combine(
        lists, manager.repos, manager.languages, manager.showNsfw, combine(query, refreshing, busy, ::Triple),
    ) { (installed, available, allLanguages), repos, languages, showNsfw, (query, refreshing, busy) ->
        ExtensionsUiState(
            repos = repos,
            updates = installed.filter { it.available != null },
            installed = installed.filter { it.available == null },
            available = available,
            query = query,
            languages = languages,
            allLanguages = allLanguages,
            showNsfw = showNsfw,
            refreshing = refreshing,
            busy = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExtensionsUiState())

    init {
        refresh()
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                manager.refreshAvailable().forEach { (repo, error) ->
                    messages.send("${repo.name}: ${error.message ?: error}")
                }
            } finally {
                refreshing.value = false
            }
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun install(row: ExtensionRow) {
        val extension = row.available ?: return
        runBusy(row.pkgName) { manager.install(extension) }
    }

    fun uninstall(row: ExtensionRow) = runBusy(row.pkgName) { manager.uninstall(row.pkgName) }

    fun addRepo(url: String) = runBusy(REPO_KEY) {
        val repo = manager.addRepo(url)
        messages.send(repo.name)
    }

    fun removeRepo(repo: ExtensionRepo) = runBusy(REPO_KEY) { manager.removeRepo(repo) }

    fun setLanguageEnabled(lang: String, enabled: Boolean) = manager.setLanguageEnabled(lang, enabled)

    fun setShowNsfw(show: Boolean) = manager.setShowNsfw(show)

    private fun runBusy(key: String, block: suspend () -> Unit) {
        if (key in busy.value) return
        viewModelScope.launch {
            busy.update { it + key }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.send(e.message ?: e.toString())
            } finally {
                busy.update { it - key }
            }
        }
    }

    companion object {
        const val REPO_KEY = "#repo"
    }
}

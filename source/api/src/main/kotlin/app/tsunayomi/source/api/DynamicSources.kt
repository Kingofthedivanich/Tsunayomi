package app.tsunayomi.source.api

import kotlinx.coroutines.flow.StateFlow

/** Sources that appear and disappear while the app runs, such as installed extensions. */
interface DynamicSources {
    /** Everything that is loaded, so titles saved from any of these sources open. */
    val sources: StateFlow<List<Source>>

    /** The subset to offer for browsing and global search, e.g. only the languages the user reads. */
    val browsable: StateFlow<List<Source>>

    /** Blocks until the sources that were installed when the app started are loaded. */
    fun awaitLoaded()
}

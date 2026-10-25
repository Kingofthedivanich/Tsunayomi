package app.tsunayomi.feature.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import app.tsunayomi.data.SourceManager
import app.tsunayomi.source.api.CatalogSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
internal class WebViewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sourceManager: SourceManager,
) : ViewModel() {
    private val routeSourceId: Long? = runCatching { savedStateHandle.toRoute<WebViewRoute>().sourceId }.getOrNull()

    /** Off the main thread: looking a source up waits until extensions and kotatsu parsers are loaded. */
    suspend fun loadHeaders(sourceId: Long): Map<String, String> = withContext(Dispatchers.IO) {
        val targetId = if (sourceId != 0L) sourceId else (routeSourceId ?: 0L)
        (sourceManager.get(targetId) as? CatalogSource)?.webHeaders() ?: emptyMap()
    }
}

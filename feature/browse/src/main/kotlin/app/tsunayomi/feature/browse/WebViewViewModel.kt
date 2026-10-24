package app.tsunayomi.feature.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import app.tsunayomi.data.SourceManager
import app.tsunayomi.source.api.CatalogSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class WebViewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sourceManager: SourceManager,
) : ViewModel() {
    private val routeSourceId: Long? = runCatching { savedStateHandle.toRoute<WebViewRoute>().sourceId }.getOrNull()

    fun getHeaders(sourceId: Long): Map<String, String> {
        val targetId = if (sourceId != 0L) sourceId else (routeSourceId ?: 0L)
        return (sourceManager.get(targetId) as? CatalogSource)?.webHeaders() ?: emptyMap()
    }
}

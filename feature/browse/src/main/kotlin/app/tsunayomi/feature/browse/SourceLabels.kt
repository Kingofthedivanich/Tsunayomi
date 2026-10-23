package app.tsunayomi.feature.browse

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.tsunayomi.source.api.Source
import app.tsunayomi.source.api.SourceIds

@Composable
internal fun Source.displayName(): String =
    if (id == SourceIds.LOCAL) stringResource(R.string.feature_browse_local_source) else name

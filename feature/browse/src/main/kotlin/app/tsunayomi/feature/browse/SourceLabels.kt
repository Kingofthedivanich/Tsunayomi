package app.tsunayomi.feature.browse

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.tsunayomi.source.api.Source
import app.tsunayomi.source.api.SourceIds
import java.util.Locale

@Composable
internal fun Source.displayName(): String =
    if (id == SourceIds.LOCAL) stringResource(R.string.feature_browse_local_source) else name

/** "English", "русский", … in the UI language; null for sources that mix languages. */
internal fun Source.languageLabel(): String? =
    lang.takeIf { it != Source.MULTI_LANG }
        ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()) }
        ?.replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** Name plus language, for places where several instances of a source appear together. */
@Composable
internal fun Source.fullName(): String = languageLabel()?.let { "${displayName()} · $it" } ?: displayName()

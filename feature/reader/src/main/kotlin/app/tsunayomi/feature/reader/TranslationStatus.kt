package app.tsunayomi.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.translation.api.TranslationException

/** A small pill above the page: progress, a fallback notice or an error with retry. Hidden when all is well. */
@Composable
internal fun TranslationStatus(state: PageTranslationState?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val (text, isError, busy) = when (state) {
        null, PageTranslationState.Loading -> Triple(stringResource(R.string.feature_reader_translating), false, true)
        is PageTranslationState.Failed -> Triple(
            stringResource(R.string.feature_reader_translation_failed, describe(state.error)),
            true,
            false,
        )
        is PageTranslationState.Done -> {
            val reason = state.translation.fallbackReason ?: return
            Triple(stringResource(R.string.feature_reader_translation_fallback, describe(reason)), false, false)
        }
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.then(if (isError) Modifier.clickable(onClick = onRetry) else Modifier),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(text.replaceFirstChar { it.uppercaseChar() }, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun describe(error: Throwable): String = when (error) {
    is TranslationException.MissingApiKey -> stringResource(R.string.feature_reader_translation_error_no_key, error.engine.label())
    is TranslationException.InvalidApiKey -> stringResource(R.string.feature_reader_translation_error_bad_key, error.engine.label())
    is TranslationException.QuotaExceeded -> stringResource(R.string.feature_reader_translation_error_quota, error.engine.label())
    is TranslationException.UnsupportedLanguage -> stringResource(R.string.feature_reader_translation_error_language, error.lang)
    is TranslationException.Network -> stringResource(R.string.feature_reader_translation_error_network)
    else -> error.message ?: error.toString()
}

private fun TranslationEngine.label(): String = when (this) {
    TranslationEngine.MlKit -> "ML Kit"
    TranslationEngine.Gemini -> "Gemini"
    else -> name
}

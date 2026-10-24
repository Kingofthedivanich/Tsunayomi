package app.tsunayomi.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.tsunayomi.core.designsystem.component.labelRes
import app.tsunayomi.core.model.ReaderMode
import kotlin.math.roundToInt

@Composable
internal fun ReaderMenu(
    visible: Boolean,
    title: String,
    chapterName: String,
    pageCount: Int,
    currentPage: Int,
    mode: ReaderMode,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onModeChange: (ReaderMode) -> Unit,
    translationOn: Boolean,
    onToggleTranslation: () -> Unit,
    incognitoOn: Boolean,
    onToggleIncognito: () -> Unit,
    onJumpTo: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MENU_ALPHA)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.feature_reader_back))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(chapterName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconToggleButton(checked = incognitoOn, onCheckedChange = { onToggleIncognito() }) {
                        Icon(
                            Icons.Outlined.VisibilityOff,
                            stringResource(if (incognitoOn) R.string.feature_reader_incognito_off else R.string.feature_reader_incognito_on),
                            tint = if (incognitoOn) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconToggleButton(checked = translationOn, onCheckedChange = { onToggleTranslation() }) {
                        Icon(
                            Icons.Outlined.Translate,
                            stringResource(if (translationOn) R.string.feature_reader_translation_off else R.string.feature_reader_translation_on),
                            tint = if (translationOn) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    ModeButton(mode, onModeChange)
                }
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MENU_ALPHA)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPreviousChapter, enabled = hasPrevious) {
                        Icon(Icons.Outlined.SkipPrevious, stringResource(R.string.feature_reader_previous_chapter))
                    }
                    Text("${currentPage + 1}", style = MaterialTheme.typography.labelMedium)
                    // Right-to-left manga: the slider fills from the right, matching the page order.
                    val direction = if (mode == ReaderMode.PagedRightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        if (pageCount > 1) {
                            Slider(
                                value = currentPage.toFloat(),
                                onValueChange = { onJumpTo(it.roundToInt()) },
                                valueRange = 0f..(pageCount - 1).toFloat(),
                                steps = (pageCount - 2).coerceAtLeast(0),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                            )
                        } else {
                            Box(Modifier.weight(1f))
                        }
                    }
                    Text("$pageCount", style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = onNextChapter, enabled = hasNext) {
                        Icon(Icons.Outlined.SkipNext, stringResource(R.string.feature_reader_next_chapter))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeButton(mode: ReaderMode, onModeChange: (ReaderMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.AutoStories, stringResource(R.string.feature_reader_mode))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReaderMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    leadingIcon = {
                        if (option == mode) Icon(Icons.Outlined.Check, contentDescription = null) else Box(Modifier)
                    },
                    onClick = {
                        expanded = false
                        onModeChange(option)
                    },
                )
            }
        }
    }
}

private const val MENU_ALPHA = 0.95f

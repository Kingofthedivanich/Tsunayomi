package app.tsunayomi.feature.reader

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tsunayomi.core.designsystem.component.MessageWithAction
import app.tsunayomi.core.model.ReaderMode

@Composable
internal fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    var menuVisible by rememberSaveable { mutableStateOf(false) }
    val navigator = remember { ReaderNavigator() }
    val focusRequester = remember { FocusRequester() }

    ImmersiveMode(systemBarsVisible = menuVisible)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!state.volumeKeys) return@onPreviewKeyEvent false
                val action = when (event.key) {
                    Key.VolumeDown -> navigator.next
                    Key.VolumeUp -> navigator.previous
                    else -> return@onPreviewKeyEvent false
                }
                // Consume both down and up, otherwise the system still changes the volume.
                if (event.type == KeyEventType.KeyDown) action()
                true
            },
    ) {
        val chapter = state.chapter
        when {
            state.error != null -> MessageWithAction(
                title = stringResource(R.string.feature_reader_error),
                message = state.error,
                actionLabel = stringResource(R.string.feature_reader_back),
                onAction = onBack,
            )
            state.loading || chapter == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.pages.isEmpty() -> MessageWithAction(
                title = stringResource(R.string.feature_reader_no_pages),
                icon = Icons.Outlined.ImageNotSupported,
                actionLabel = stringResource(R.string.feature_reader_back),
                onAction = onBack,
            )
            else -> key(chapter.id, state.mode) {
                val toggleMenu = { menuVisible = !menuVisible }
                when (state.mode) {
                    ReaderMode.Webtoon -> WebtoonReader(
                        pages = state.pages,
                        initialPage = state.initialPage,
                        nextChapter = state.nextChapter,
                        navigator = navigator,
                        onPageChanged = viewModel::onPageChanged,
                        onToggleMenu = toggleMenu,
                        onNextChapter = viewModel::openNextChapter,
                    )
                    else -> PagedReader(
                        pages = state.pages,
                        initialPage = state.initialPage,
                        rightToLeft = state.mode == ReaderMode.PagedRightToLeft,
                        vertical = state.mode == ReaderMode.Vertical,
                        nextChapter = state.nextChapter,
                        navigator = navigator,
                        onPageChanged = viewModel::onPageChanged,
                        onToggleMenu = toggleMenu,
                        onNextChapter = viewModel::openNextChapter,
                    )
                }
            }
        }

        if (!menuVisible && chapter != null && state.pages.isNotEmpty()) {
            Text(
                text = "${currentPage + 1} / ${state.pages.size}",
                style = MaterialTheme.typography.labelSmall.merge(
                    TextStyle(color = Color.White, shadow = Shadow(Color.Black, blurRadius = 4f)),
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 4.dp),
            )
        }

        ReaderMenu(
            visible = menuVisible,
            title = state.manga?.title.orEmpty(),
            chapterName = chapter?.name.orEmpty(),
            pageCount = state.pages.size,
            currentPage = currentPage,
            mode = state.mode,
            hasPrevious = state.previousChapter != null,
            hasNext = state.nextChapter != null,
            onBack = onBack,
            onModeChange = viewModel::setMode,
            onJumpTo = { navigator.jumpTo(it) },
            onPreviousChapter = viewModel::openPreviousChapter,
            onNextChapter = viewModel::openNextChapter,
        )
    }
}

/** Hides system bars while reading and keeps the screen on; everything is restored when leaving the reader. */
@Composable
private fun ImmersiveMode(systemBarsVisible: Boolean) {
    val view = LocalView.current
    val window = LocalActivity.current?.window ?: return
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }

    DisposableEffect(controller) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        view.keepScreenOn = true
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
    LaunchedEffect(systemBarsVisible) {
        if (systemBarsVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

package app.tsunayomi.feature.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import app.tsunayomi.core.model.Chapter
import app.tsunayomi.source.api.SPage
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/**
 * One page per screen, horizontal (LTR or RTL) or vertical. The last pager item is the end-of-chapter screen.
 */
@Composable
internal fun PagedReader(
    pages: List<SPage>,
    initialPage: Int,
    rightToLeft: Boolean,
    vertical: Boolean,
    nextChapter: Chapter?,
    navigator: ReaderNavigator,
    onPageChanged: (Int) -> Unit,
    onToggleMenu: () -> Unit,
    onNextChapter: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size + 1 }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect(onPageChanged)
    }
    SideEffect {
        navigator.next = {
            if (pagerState.currentPage < pagerState.pageCount - 1) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            } else if (nextChapter != null) {
                onNextChapter()
            }
        }
        navigator.previous = {
            if (pagerState.currentPage > 0) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
        navigator.jumpTo = { page -> scope.launch { pagerState.scrollToPage(page) } }
    }

    val onTap: (Offset, IntSize) -> Unit = { offset, size ->
        val zone = if (vertical) tapZone(offset.y, size.height) else tapZone(offset.x, size.width)
        // In right-to-left reading the left edge moves forward.
        val forwardZone = if (rightToLeft && !vertical) TapZone.Start else TapZone.End
        when (zone) {
            TapZone.Middle -> onToggleMenu()
            forwardZone -> navigator.next()
            else -> navigator.previous()
        }
    }

    val pageContent: @Composable (Int) -> Unit = { index ->
        if (index < pages.size) {
            ZoomablePage(pages[index], onTap)
        } else {
            TappableBox(onTap) { ChapterEndPage(nextChapter, onNextChapter) }
        }
    }

    if (vertical) {
        VerticalPager(state = pagerState, beyondViewportPageCount = PRELOAD_PAGES, key = { it }) { pageContent(it) }
    } else {
        HorizontalPager(
            state = pagerState,
            reverseLayout = rightToLeft,
            beyondViewportPageCount = PRELOAD_PAGES,
            key = { it },
        ) { pageContent(it) }
    }
}

@Composable
private fun ZoomablePage(page: SPage, onTap: (Offset, IntSize) -> Unit) {
    val context = LocalContext.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var failed by remember(page) { mutableStateOf(false) }
    var attempt by remember(page) { mutableIntStateOf(0) }
    val request = remember(page, attempt) {
        page.toImageRequest(context) {
            listener(onError = { _, _ -> failed = true }, onSuccess = { _, _ -> failed = false })
        }
    }
    val imageState = rememberZoomableImageState(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = MAX_ZOOM)))

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it },
        contentAlignment = Alignment.Center,
    ) {
        ZoomableAsyncImage(
            model = request,
            contentDescription = null,
            state = imageState,
            modifier = Modifier.fillMaxSize(),
            onClick = { onTap(it, size) },
        )
        when {
            failed -> PageError(onRetry = {
                failed = false
                attempt++
            })
            !imageState.isImageDisplayed -> CircularProgressIndicator()
        }
    }
}

@Composable
internal fun PageError(onRetry: () -> Unit) {
    OutlinedButton(onClick = onRetry) {
        Text(stringResource(R.string.feature_reader_page_failed), color = Color.White)
    }
}

@Composable
private fun TappableBox(onTap: (Offset, IntSize) -> Unit, content: @Composable () -> Unit) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) { detectTapGestures { onTap(it, size) } },
    ) { content() }
}

private const val PRELOAD_PAGES = 3
private const val MAX_ZOOM = 4f

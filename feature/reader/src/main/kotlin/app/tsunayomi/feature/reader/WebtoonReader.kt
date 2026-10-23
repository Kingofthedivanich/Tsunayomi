package app.tsunayomi.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.tsunayomi.core.model.Chapter
import app.tsunayomi.source.api.SPage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.imageLoader
import coil3.size.Dimension
import coil3.size.Size
import kotlinx.coroutines.launch

/**
 * Continuous vertical strip. Pages are decoded at screen width, so tall webtoon strips stay within memory.
 * Zoom is not supported here yet.
 */
@Composable
internal fun WebtoonReader(
    pages: List<SPage>,
    initialPage: Int,
    nextChapter: Chapter?,
    navigator: ReaderNavigator,
    translatedImage: (Int) -> Bitmap?,
    onPageChanged: (Int) -> Unit,
    onToggleMenu: () -> Unit,
    onNextChapter: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    // Width / height of each page once seen. Kept for the whole chapter so a page never changes height again —
    // not when it reloads, scrolls back into view, or is swapped for its translated copy. Height jumps would shift
    // the list, report a new "current" page, trigger more translations and cascade to the end of the chapter.
    val aspectRatios = remember { mutableStateMapOf<Int, Float>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val decodeSize = remember(widthPx) { Size(Dimension(widthPx), Dimension.Undefined) }

        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
                onPageChanged(index)
                // Warm the cache for the next pages with the exact request the list will make.
                for (ahead in 1..PREFETCH_PAGES) {
                    val page = pages.getOrNull(index + ahead) ?: break
                    context.imageLoader.enqueue(page.toImageRequest(context) { size(decodeSize) })
                }
            }
        }
        SideEffect {
            navigator.next = { scope.launch { listState.animateScrollBy(heightPx * SCROLL_FRACTION) } }
            navigator.previous = { scope.launch { listState.animateScrollBy(-heightPx * SCROLL_FRACTION) } }
            navigator.jumpTo = { page -> scope.launch { listState.scrollToItem(page) } }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        when (tapZone(offset.y, size.height)) {
                            TapZone.Start -> navigator.previous()
                            TapZone.End -> navigator.next()
                            TapZone.Middle -> onToggleMenu()
                        }
                    }
                },
        ) {
            items(pages.size, key = { it }) { index ->
                WebtoonPage(
                    page = pages[index],
                    decodeSize = decodeSize,
                    translatedImage = translatedImage(index),
                    aspectRatio = aspectRatios[index],
                    onAspectRatio = { aspectRatios[index] = it },
                )
            }
            item(key = "end") {
                Box(Modifier.fillMaxWidth().height(maxHeight)) { ChapterEndPage(nextChapter, onNextChapter) }
            }
        }
    }
}

@Composable
private fun WebtoonPage(
    page: SPage,
    decodeSize: Size,
    translatedImage: Bitmap?,
    aspectRatio: Float?,
    onAspectRatio: (Float) -> Unit,
) {
    val context = LocalContext.current
    var attempt by remember(page) { mutableIntStateOf(0) }
    var painterState by remember(page) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val request = remember(page, attempt, decodeSize) { page.toImageRequest(context) { size(decodeSize) } }

    Box(contentAlignment = Alignment.Center) {
        AsyncImage(
            model = translatedImage ?: request,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            onState = { state ->
                painterState = state
                if (state is AsyncImagePainter.State.Success && aspectRatio == null) {
                    val size = state.painter.intrinsicSize
                    if (size.width > 0f && size.height > 0f) onAspectRatio(size.width / size.height)
                }
            },
            // The height is fixed by the page's real proportions once known; before that, a typical page height
            // keeps the list from composing everything at once.
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio ?: PLACEHOLDER_RATIO),
        )
        when (painterState) {
            is AsyncImagePainter.State.Error -> PageError(onRetry = { attempt++ })
            is AsyncImagePainter.State.Success -> Unit
            else -> CircularProgressIndicator()
        }
    }
}

private const val PREFETCH_PAGES = 3
private const val SCROLL_FRACTION = 0.8f
private const val PLACEHOLDER_RATIO = 0.7f

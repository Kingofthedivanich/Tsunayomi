package app.tsunayomi.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

/** Minimal data a grid cell needs; screens map their own models onto it. */
data class MangaGridItem(
    val key: String,
    val title: String,
    val coverUrl: String?,
    /** Small label in the corner of the cover, e.g. the unread chapter count or reading progress. */
    val badge: String? = null,
    val badgeHighlighted: Boolean = true,
)

/** Adaptive grid of covers: more columns on wider screens. Calls [onLoadMore] near the end. */
@Composable
fun MangaGrid(
    items: List<MangaGridItem>,
    onClick: (MangaGridItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    onLoadMore: (() -> Unit)? = null,
    onContinue: ((MangaGridItem) -> Unit)? = null,
    footer: (LazyGridScope.() -> Unit)? = null,
) {
    val state = rememberLazyGridState()
    if (onLoadMore != null) {
        val nearEnd by remember {
            derivedStateOf {
                val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                last >= state.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
            }
        }
        LaunchedEffect(nearEnd, items.size) { if (nearEnd && items.isNotEmpty()) onLoadMore() }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        items(items.size, key = { items[it].key }) { index ->
            val item = items[index]
            MangaCover(
                item,
                onClick = { onClick(item) },
                onContinue = onContinue?.let { { it(item) } },
                modifier = Modifier.padding(4.dp),
            )
        }
        footer?.invoke(this)
    }
}

@Composable
fun MangaCover(
    item: MangaGridItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onContinue: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box {
            CoverImage(
                url = item.coverUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(COVER_ASPECT_RATIO)
                    .clip(RoundedCornerShape(8.dp)),
            )
            if (item.badge != null) {
                val badgeContainerColor = if (item.badgeHighlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                }
                val badgeContentColor = if (item.badgeHighlighted) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = item.badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeContentColor,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(badgeContainerColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (onContinue != null) {
                FilledIconButton(
                    onClick = onContinue,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(32.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun CoverImage(url: String?, modifier: Modifier = Modifier) {
    val placeholder: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_ASPECT_RATIO)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (url == null) {
        Box(modifier) { placeholder() }
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { placeholder() },
        error = { placeholder() },
    )
}

const val COVER_ASPECT_RATIO = 2f / 3f
private const val LOAD_MORE_THRESHOLD = 6

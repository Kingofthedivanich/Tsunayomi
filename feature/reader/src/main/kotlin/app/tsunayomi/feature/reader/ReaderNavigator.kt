package app.tsunayomi.feature.reader

import androidx.compose.runtime.Stable

/**
 * Lets controls outside the page view (volume keys, the page slider) drive whichever reader mode is shown.
 * The active mode installs its handlers on composition.
 */
@Stable
internal class ReaderNavigator {
    var next: () -> Unit = {}
    var previous: () -> Unit = {}
    var jumpTo: (page: Int) -> Unit = {}
}

/** Which third of the screen a tap landed in, along the reading axis. */
internal enum class TapZone { Start, Middle, End }

internal fun tapZone(position: Float, length: Int): TapZone = when {
    length <= 0 -> TapZone.Middle
    position < length * EDGE_FRACTION -> TapZone.Start
    position > length * (1 - EDGE_FRACTION) -> TapZone.End
    else -> TapZone.Middle
}

private const val EDGE_FRACTION = 0.3f

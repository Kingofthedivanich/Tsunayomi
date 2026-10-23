package app.tsunayomi.core.designsystem.component

import androidx.annotation.StringRes
import app.tsunayomi.core.designsystem.R
import app.tsunayomi.core.model.ReaderMode

@get:StringRes
val ReaderMode.labelRes: Int
    get() = when (this) {
        ReaderMode.PagedLeftToRight -> R.string.reader_mode_paged_ltr
        ReaderMode.PagedRightToLeft -> R.string.reader_mode_paged_rtl
        ReaderMode.Vertical -> R.string.reader_mode_vertical
        ReaderMode.Webtoon -> R.string.reader_mode_webtoon
    }

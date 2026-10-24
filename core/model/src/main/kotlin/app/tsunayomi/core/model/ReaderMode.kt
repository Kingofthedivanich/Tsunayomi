package app.tsunayomi.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ReaderMode {
    PagedLeftToRight,
    PagedRightToLeft,
    Vertical,
    Webtoon,
}

package app.tsunayomi.translation.core

import app.tsunayomi.translation.api.NormalizedBox
import app.tsunayomi.translation.api.TranslatedRegion
import app.tsunayomi.translation.core.TranslationRenderer.changesText
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationRendererTest {
    private fun region(source: String, translated: String) =
        TranslatedRegion(NormalizedBox(0f, 0f, 1f, 1f), source, translated)

    @Test
    fun `unchanged text is not covered`() {
        assertFalse(region("Page 2!", "Page 2!").changesText())
        assertFalse(region("Vol.1 Ch.1 · 1/8", "Vol.1 CH.1: 1/8").changesText())
    }

    @Test
    fun `real translations are drawn`() {
        assertTrue(region("こんにちは", "Добрый день").changesText())
        assertTrue(region("Chapter 2", "Глава 2").changesText())
    }
}

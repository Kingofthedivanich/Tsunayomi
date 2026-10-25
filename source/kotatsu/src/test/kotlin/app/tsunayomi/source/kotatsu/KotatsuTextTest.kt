package app.tsunayomi.source.kotatsu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KotatsuTextTest {

    @Test
    fun `authorNames unwraps a JSON array and keeps plain names`() {
        assertEquals(listOf("Markellaha"), authorNames("""["Markellaha"]"""))
        assertEquals(listOf("A", "B"), authorNames("""["A", "B"]"""))
        assertEquals(listOf("Fujimoto Tatsuki"), authorNames("Fujimoto Tatsuki"))
    }

    @Test
    fun `plainText turns HTML paragraphs into lines`() {
        val html = """<p class="x">I 'transmigrated' into a world.</p><p>I spent 30 years &amp; more.<br>End</p>"""

        assertEquals("I 'transmigrated' into a world.\n\nI spent 30 years & more.\nEnd", plainText(html))
        assertEquals("Plain text", plainText("  Plain text "))
    }
}

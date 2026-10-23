package app.tsunayomi.source.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceIdTest {
    @Test
    fun `id is stable for the same inputs`() {
        assertEquals(sourceIdOf("MangaDex", "en"), sourceIdOf("MangaDex", "en"))
    }

    @Test
    fun `id ignores name case`() {
        assertEquals(sourceIdOf("MangaDex", "en"), sourceIdOf("mangadex", "en"))
    }

    @Test
    fun `id differs by language and version`() {
        val base = sourceIdOf("MangaDex", "en")
        assertNotEquals(base, sourceIdOf("MangaDex", "ja"))
        assertNotEquals(base, sourceIdOf("MangaDex", "en", versionId = 2))
    }

    @Test
    fun `id is never negative`() {
        listOf("a", "b", "MangaDex", "Komga", "Local").forEach { name ->
            assertTrue(sourceIdOf(name, "en") >= 0)
        }
    }
}

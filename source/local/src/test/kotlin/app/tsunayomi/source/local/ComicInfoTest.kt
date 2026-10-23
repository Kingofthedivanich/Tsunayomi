package app.tsunayomi.source.local

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ComicInfoTest {
    @Test
    fun `reads known fields`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <ComicInfo xmlns:ty="http://www.w3.org/2001/XMLSchema">
              <Series>Test Series</Series>
              <Summary>About something.</Summary>
              <Writer>Author A</Writer>
              <Penciller>Artist B</Penciller>
              <Genre>Action, Comedy</Genre>
              <Tags>Comedy, School</Tags>
              <LanguageISO>ja-JP</LanguageISO>
              <Manga>YesAndRightToLeft</Manga>
              <ty:PublishingStatusTachiyomi>Ongoing</ty:PublishingStatusTachiyomi>
            </ComicInfo>
        """.trimIndent()

        val info = ComicInfo.parse(xml.byteInputStream())!!

        assertEquals("Test Series", info.series)
        assertEquals("About something.", info.summary)
        assertEquals("Author A", info.writer)
        assertEquals("Artist B", info.penciller)
        assertEquals(listOf("Action", "Comedy", "School"), info.genres)
        assertEquals("ja", info.languageIso)
        assertEquals(true, info.rightToLeft)
        assertEquals("Ongoing", info.status)
    }

    @Test
    fun `rejects doctype declarations`() {
        val xml = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><ComicInfo><Series>&e;</Series></ComicInfo>"""
        assertNull(ComicInfo.parse(xml.byteInputStream()))
    }

    @Test
    fun `returns null for broken xml`() {
        assertNull(ComicInfo.parse("not xml".byteInputStream()))
    }
}

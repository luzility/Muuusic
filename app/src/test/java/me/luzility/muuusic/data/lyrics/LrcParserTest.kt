package me.luzility.muuusic.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    @Test fun parsesTimestampsAndText() {
        val lines = LrcParser.parse(
            """
            [ar:Tarkan]
            [00:12.34] Birinci satır
            [01:02.5]İkinci
            [00:05.123]
            """.trimIndent()
        )
        assertEquals(listOf(5_123L, 12_340L, 62_500L), lines.map { it.timeMs })
        assertEquals(listOf("", "Birinci satır", "İkinci"), lines.map { it.text })
    }

    @Test fun repeatsLinesWithSeveralTags() {
        val lines = LrcParser.parse("[00:10.00][00:30.00]Nakarat")
        assertEquals(listOf(10_000L, 30_000L), lines.map { it.timeMs })
        assertEquals(listOf("Nakarat", "Nakarat"), lines.map { it.text })
    }

    @Test fun appliesOffset() {
        val lines = LrcParser.parse("[offset:+500]\n[00:10.00]Line")
        assertEquals(9_500L, lines.single().timeMs)
    }

    @Test fun ignoresLinesWithoutTimestamps() {
        assertEquals(emptyList<LyricLine>(), LrcParser.parse("just text\n[ti:Title]"))
    }
}

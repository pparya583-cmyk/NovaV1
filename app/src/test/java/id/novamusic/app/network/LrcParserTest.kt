package id.novamusic.app.network

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    @Test fun parsesCentisecondsAndMilliseconds() {
        val result = LrcParser.parse("[00:01.23]First\n[00:02.456]Second")
        assertEquals(listOf(LyricLine(1230, "First"), LyricLine(2456, "Second")), result)
    }

    @Test fun sortsOutOfOrderLines() {
        val result = LrcParser.parse("[00:04.00]Later\n[00:01.00]Earlier")
        assertEquals(listOf(1000L, 4000L), result.map { it.timeMs })
    }

    @Test fun ignoresMalformedTimestamps() {
        val result = LrcParser.parse("[00:72.00]Bad\nnot a timestamp\n[00:01.00]Good")
        assertEquals(listOf(LyricLine(1000, "Good")), result)
    }

    @Test fun supportsLongMinuteValuesAndEmptyLines() {
        val result = LrcParser.parse("[123:05.00]\n[01:02.00]Text")
        assertEquals(2, result.size)
        val empty = result.first { it.text.isEmpty() }
        assertEquals(7_385_000L, empty.timeMs)
    }
}

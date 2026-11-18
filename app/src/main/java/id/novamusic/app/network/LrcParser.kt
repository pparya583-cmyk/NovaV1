package id.novamusic.app.network

data class LyricLine(val timeMs: Long, val text: String)

object LrcParser {
    private val timestamp = Regex("""\[(\d{1,3}):(\d{2})[.](\d{2,3})\](.*)""")

    fun parse(input: String): List<LyricLine> = input.lineSequence()
        .mapNotNull { raw ->
            val match = timestamp.matchEntire(raw.trim()) ?: return@mapNotNull null
            val (minutes, seconds, fraction, words) = match.destructured
            val sec = seconds.toIntOrNull() ?: return@mapNotNull null
            if (sec !in 0..59) return@mapNotNull null
            val millis = fraction.padEnd(3, '0').take(3).toIntOrNull() ?: return@mapNotNull null
            LyricLine(minutes.toLong() * 60_000 + sec * 1000L + millis, words.trim())
        }
        .sortedBy { it.timeMs }
        .toList()
}

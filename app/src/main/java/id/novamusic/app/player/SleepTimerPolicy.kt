package id.novamusic.app.player

object SleepTimerPolicy {
    fun remainingMillis(deadlineEpochMs: Long, nowEpochMs: Long): Long =
        (deadlineEpochMs - nowEpochMs).coerceAtLeast(0L)

    fun remainingMinutes(deadlineEpochMs: Long, nowEpochMs: Long): Int {
        val millis = remainingMillis(deadlineEpochMs, nowEpochMs)
        return if (millis == 0L) 0 else ((millis + 59_999L) / 60_000L).toInt()
    }
}

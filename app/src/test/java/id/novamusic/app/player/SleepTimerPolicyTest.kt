package id.novamusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerPolicyTest {
    @Test fun expiredDeadlineHasNoRemainingTime() {
        assertEquals(0L, SleepTimerPolicy.remainingMillis(900L, 1_000L))
        assertEquals(0, SleepTimerPolicy.remainingMinutes(900L, 1_000L))
    }

    @Test fun remainingMinutesRoundUpForDisplay() {
        assertEquals(2, SleepTimerPolicy.remainingMinutes(61_001L, 1_000L))
        assertEquals(1, SleepTimerPolicy.remainingMinutes(60_001L, 1_000L))
        assertEquals(0, SleepTimerPolicy.remainingMinutes(1_000L, 1_000L))
    }

    @Test fun remainingMillisNeverReturnsNegative() {
        assertEquals(0L, SleepTimerPolicy.remainingMillis(10L, 100L))
        assertEquals(500L, SleepTimerPolicy.remainingMillis(600L, 100L))
    }
}

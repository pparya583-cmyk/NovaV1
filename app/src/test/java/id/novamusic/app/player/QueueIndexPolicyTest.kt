package id.novamusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueIndexPolicyTest {
    @Test fun playNextGoesAfterCurrent() {
        assertEquals(3, QueueIndexPolicy.playNextInsertionIndex(2, 5))
    }

    @Test fun playNextAtLastItemAppends() {
        assertEquals(5, QueueIndexPolicy.playNextInsertionIndex(4, 5))
    }

    @Test fun playNextWithNoCurrentItemStartsAtZero() {
        assertEquals(0, QueueIndexPolicy.playNextInsertionIndex(-1, 0))
        assertEquals(0, QueueIndexPolicy.playNextInsertionIndex(-1, 4))
    }

    @Test fun restoredIndexIsClampedToAvailableItems() {
        assertEquals(0, QueueIndexPolicy.restoredCurrentIndex(-5, 3))
        assertEquals(2, QueueIndexPolicy.restoredCurrentIndex(9, 3))
    }

    @Test fun emptyRestoredQueueHasNoCurrentIndex() {
        assertNull(QueueIndexPolicy.restoredCurrentIndex(0, 0))
    }
}

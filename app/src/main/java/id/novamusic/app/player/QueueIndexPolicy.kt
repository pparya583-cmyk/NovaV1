package id.novamusic.app.player

/**
 * Small deterministic rules for queue insertion and restoration.
 * Queue indices always refer to the visible playlist order.
 */
object QueueIndexPolicy {
    fun playNextInsertionIndex(currentIndex: Int, itemCount: Int): Int {
        if (itemCount <= 0) return 0
        return (currentIndex + 1).coerceIn(0, itemCount)
    }

    fun restoredCurrentIndex(savedIndex: Int, restoredItemCount: Int): Int? {
        if (restoredItemCount <= 0) return null
        return savedIndex.coerceIn(0, restoredItemCount - 1)
    }
}

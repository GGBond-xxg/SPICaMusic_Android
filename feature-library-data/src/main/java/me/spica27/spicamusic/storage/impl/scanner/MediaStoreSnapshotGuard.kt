package me.spica27.spicamusic.storage.impl.scanner

internal class MediaStoreQueryUnavailableException(
    message: String,
) : IllegalStateException(message)

/**
 * Prevents one transient empty MediaStore query from being treated as a complete library deletion.
 *
 * A non-empty snapshot is always accepted and resets the guard. When an existing library suddenly
 * becomes empty, the empty result must be observed repeatedly across a minimum time window before
 * it is accepted as a real deletion.
 */
class MediaStoreSnapshotGuard(
    private val minimumConfirmations: Int,
    private val minimumConfirmationWindowMs: Long,
) {
    private var firstEmptySnapshotAtMs: Long? = null
    private var emptySnapshotConfirmations = 0

    fun shouldAccept(
        existingItemCount: Int,
        rawSnapshotItemCount: Int,
        nowMs: Long,
    ): Boolean {
        if (existingItemCount <= 0 || rawSnapshotItemCount > 0) {
            reset()
            return true
        }

        val firstSeenAt = firstEmptySnapshotAtMs
        if (firstSeenAt == null || nowMs < firstSeenAt) {
            firstEmptySnapshotAtMs = nowMs
            emptySnapshotConfirmations = 1
            return false
        }

        emptySnapshotConfirmations++
        val confirmationWindowReached = nowMs - firstSeenAt >= minimumConfirmationWindowMs
        val confirmationsReached = emptySnapshotConfirmations >= minimumConfirmations
        if (confirmationWindowReached && confirmationsReached) {
            reset()
            return true
        }
        return false
    }

    private fun reset() {
        firstEmptySnapshotAtMs = null
        emptySnapshotConfirmations = 0
    }
}

package me.spica27.spicamusic.storage.impl.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreSnapshotGuardTest {
    private val guard =
        MediaStoreSnapshotGuard(
            minimumConfirmations = 3,
            minimumConfirmationWindowMs = 15_000L,
        )

    @Test
    fun `single transient empty snapshot is rejected`() {
        assertFalse(
            guard.shouldAccept(
                existingItemCount = 94,
                rawSnapshotItemCount = 0,
                nowMs = 1_000L,
            ),
        )
    }

    @Test
    fun `empty snapshot is accepted only after repeated delayed confirmation`() {
        assertFalse(guard.shouldAccept(94, 0, 1_000L))
        assertFalse(guard.shouldAccept(94, 0, 6_000L))
        assertFalse(guard.shouldAccept(94, 0, 11_000L))
        assertTrue(guard.shouldAccept(94, 0, 16_000L))
    }

    @Test
    fun `non empty snapshot immediately resets pending empty confirmation`() {
        assertFalse(guard.shouldAccept(94, 0, 1_000L))
        assertTrue(guard.shouldAccept(94, 95, 2_000L))
        assertFalse(guard.shouldAccept(95, 0, 20_000L))
    }

    @Test
    fun `empty snapshot is valid when database is already empty`() {
        assertTrue(guard.shouldAccept(0, 0, 1_000L))
    }
}

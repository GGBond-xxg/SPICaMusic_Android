package me.spica27.spicamusic.service

/** Monotonic countdown policy, independent of the UI and playback implementation. */
internal class SleepTimerState {
    var durationMs: Long? = null
        private set
    var remainingMs: Long? = null
        private set
    var waitingForSongEnd = false
        private set
    private var deadlineMs = 0L

    fun start(
        durationMs: Long,
        nowMs: Long,
    ) {
        cancel()
        if (durationMs <= 0L) return
        this.durationMs = durationMs
        remainingMs = durationMs
        deadlineMs = nowMs + durationMs
    }

    /** Returns true only when playback should stop immediately. */
    fun tick(
        nowMs: Long,
        finishCurrentSong: Boolean,
        canFinishSong: Boolean,
    ): Boolean {
        if (durationMs == null) return false
        remainingMs = (deadlineMs - nowMs).coerceAtLeast(0L)
        if (remainingMs != 0L) return false
        if (finishCurrentSong && canFinishSong) {
            waitingForSongEnd = true
            return false
        }
        cancel()
        return true
    }

    fun songEnded(): Boolean {
        if (!waitingForSongEnd) return false
        cancel()
        return true
    }

    fun cancel() {
        durationMs = null
        remainingMs = null
        waitingForSongEnd = false
    }
}

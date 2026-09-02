package me.spica27.spicamusic.storage.impl.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPathScopeTest {
    @Test
    fun noConfiguredFoldersAllowsEveryAudioPath() {
        val scope = ScanPathScope.fromConfiguredFolders(0, emptyList())

        assertTrue(scope.contains("/storage/emulated/0/Music/song.flac"))
    }

    @Test
    fun configuredFolderOnlyAllowsFilesInsideThatFolder() {
        val scope =
            ScanPathScope.fromConfiguredFolders(
                configuredFolderCount = 1,
                rawPathPrefixes = listOf("/storage/emulated/0/Music"),
            )

        assertTrue(scope.contains("/storage/emulated/0/Music/song.flac"))
        assertTrue(scope.contains("/storage/emulated/0/Music/Album/song.mp3"))
        assertFalse(scope.contains("/storage/emulated/0/MusicBackup/song.mp3"))
        assertFalse(scope.contains("/storage/emulated/0/Download/song.mp3"))
    }

    @Test
    fun unresolvedConfiguredFolderDoesNotFallBackToFullDevice() {
        val scope = ScanPathScope.fromConfiguredFolders(1, listOf(null))

        assertTrue(scope.restricted)
        assertFalse(scope.contains("/storage/emulated/0/Download/song.mp3"))
    }
}

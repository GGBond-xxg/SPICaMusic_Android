package me.spica27.spicamusic.ui.widget

import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Artwork identifies a lyric result; it is content, so it has no glass layer. */
@Composable
internal fun LyricSourceArtwork(artwork: String) {
    AudioCover(
        uri = artwork.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let(Uri::parse),
        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
        placeHolder = {
            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}

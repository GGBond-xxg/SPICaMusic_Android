package me.spica27.spicamusic.ui.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import me.spica27.spicamusic.R

/** Keep the source, flying capsule and input placeholder identical at transition hand-off. */
@Composable
internal fun SearchPrompt(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.finder_search_title),
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

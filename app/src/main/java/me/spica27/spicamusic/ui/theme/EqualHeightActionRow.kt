package me.spica27.spicamusic.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Children use weight and fillMaxHeight so a wrapped label sets the height of the pair. */
@Composable
internal fun EqualHeightActionRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Spacing.Small),
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier.height(IntrinsicSize.Min), horizontalArrangement = horizontalArrangement, content = content)
}

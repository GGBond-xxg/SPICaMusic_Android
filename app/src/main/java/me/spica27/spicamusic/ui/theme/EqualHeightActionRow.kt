package me.spica27.spicamusic.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Children use weight and fillMaxHeight so a wrapped label sets the height of the pair. */
@Composable
internal fun EqualHeightActionRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Spacing.Small),
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier.height(IntrinsicSize.Min), horizontalArrangement = horizontalArrangement, content = content)
}

/** Keep multi-action menus readable and measure each line at its actual allocated width. */
@Composable
internal fun AdaptiveActionGrid(actions: List<@Composable (Modifier) -> Unit>) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val columns = (maxWidth / (96.dp * LocalDensity.current.fontScale)).toInt().coerceIn(1, 3)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            actions.chunked(columns).forEach { line ->
                EqualHeightActionRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                    line.forEach { action -> action(Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

package me.spica27.spicamusic.ui.about

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import me.spica27.navkit.scene.StackScene
import me.spica27.spicamusic.R
import me.spica27.spicamusic.ui.theme.Spacing

internal data class SponsorWallet(
    val network: String,
    val address: String,
)

internal val sponsorWallets =
    listOf(
        SponsorWallet("Solana · SOL", "GseMb4yCgfhyMvkA7jP6QhMe4e5nMPnxgJqqrA8Aq7eJ"),
        SponsorWallet("Ethereum · ETH / ERC20", "0xcB2f6fc5eF905e89cDeE7F2eB59faD9A93043324"),
        SponsorWallet("TON", "UQATTF8wVv_Q8x42OYyDOUcM1Ti0HA-VdZ0b5zUd78_lEYqj"),
        SponsorWallet("TRON · TRX / TRC20", "TXDFyQKRSLt6dmbs3tcJbEJbcHsokbgKSn"),
        SponsorWallet("Sui · SUI", "0xd61db83d28fc0da34e55b9488d3927fc5b6515cc96c6109c0f8ed451980af4bb"),
        SponsorWallet("Bitcoin · BTC", "1BhMBUVLySJg3qNgFNxYPFMGbcd4KFxkrK"),
        SponsorWallet("Dogecoin · DOGE", "DEJ6MMqAjX55YfXXtFQVeYrCbadntYwZCs"),
    )

class SponsorScene : StackScene() {
    @Composable
    override fun Content() {
        val clipboard = LocalClipboard.current
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var qrWallet by remember { mutableStateOf<SponsorWallet?>(null) }

        AboutScaffold(title = stringResource(R.string.sponsor_title)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    Text(
                        text = stringResource(R.string.sponsor_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.sponsor_network_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            items(sponsorWallets, key = { it.network }) { wallet ->
                AboutSectionCard(title = wallet.network) {
                    SelectionContainer {
                        Text(
                            text = wallet.address,
                            modifier = Modifier.padding(horizontal = Spacing.Large),
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    textDirection = TextDirection.Ltr,
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipData.newPlainText(wallet.network, wallet.address).toClipEntry(),
                                    )
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.sponsor_copied, wallet.network),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            },
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Text(
                                text = stringResource(R.string.sponsor_copy_address),
                                modifier = Modifier.padding(start = Spacing.Small),
                            )
                        }
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = { qrWallet = wallet },
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null)
                            Text(stringResource(R.string.sponsor_qr), modifier = Modifier.padding(start = Spacing.Small))
                        }
                    }
                }
            }
        }
        qrWallet?.let { wallet ->
            val matrix = remember(wallet.address) { QRCodeWriter().encode(wallet.address, BarcodeFormat.QR_CODE, 0, 0) }
            val qrBackground = MaterialTheme.colorScheme.surfaceContainerLow
            val qrForeground = MaterialTheme.colorScheme.onSurface
            AlertDialog(
                onDismissRequest = { qrWallet = null },
                title = { Text(wallet.network) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).background(qrBackground)) {
                            val scale = (size.width / matrix.width).toInt().coerceAtLeast(1).toFloat()
                            val inset = (size.width - matrix.width * scale) / 2
                            for (y in 0 until matrix.height) {
                                for (x in 0 until matrix.width) {
                                    if (matrix[x, y]) {
                                        drawRect(
                                            qrForeground,
                                            Offset(inset + x * scale, inset + y * scale),
                                            Size(scale, scale),
                                        )
                                    }
                                }
                            }
                        }
                        Text(wallet.address, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
                    }
                },
                confirmButton = { TextButton(onClick = { qrWallet = null }) { Text(stringResource(R.string.close)) } },
            )
        }
    }
}

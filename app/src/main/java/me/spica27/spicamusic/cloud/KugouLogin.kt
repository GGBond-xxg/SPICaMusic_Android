package me.spica27.spicamusic.cloud

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.spica27.spicamusic.R
import org.koin.compose.koinInject

@Composable
internal fun KugouLogin(
    modifier: Modifier = Modifier,
    onLogin: (RemoteMusicAccount) -> Unit,
    onCancel: (() -> Unit)?,
) {
    var sms by remember { mutableStateOf(true) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { sms = true }, enabled = !sms) { Text(stringResource(R.string.kugou_sms_tab)) }
            TextButton(onClick = { sms = false }, enabled = sms) { Text(stringResource(R.string.kugou_qr_tab)) }
        }
        if (sms) {
            KugouSmsLogin(Modifier.weight(1f), onLogin, onCancel)
        } else {
            KugouQrLogin(Modifier.weight(1f), onLogin, onCancel)
        }
    }
}

@Composable
private fun KugouQrLogin(
    modifier: Modifier = Modifier,
    onLogin: (RemoteMusicAccount) -> Unit,
    onCancel: (() -> Unit)?,
) {
    val client: KugouClient = koinInject()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnLogin by rememberUpdatedState(onLogin)
    var revision by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var message by remember { mutableStateOf(cloudText(R.string.kugou_qr_creating)) }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(revision, lifecycle) {
        bitmap = null
        saved = false
        loading = true
        message = cloudText(R.string.kugou_qr_creating)
        try {
            val key = client.createQr()
            bitmap =
                withContext(Dispatchers.Default) {
                    val matrix =
                        QRCodeWriter().encode(
                            "https://h5.kugou.com/apps/loginQRCode/html/index.html?qrcode=$key",
                            BarcodeFormat.QR_CODE,
                            600,
                            600,
                        )
                    Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).apply {
                        val pixels =
                            IntArray(
                                600 * 600,
                            ) { i -> if (matrix[i % 600, i / 600]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                        setPixels(pixels, 0, 600, 0, 0, 600, 600)
                    }
                }
            loading = false
            message = cloudText(R.string.kugou_qr_wait)
            val deadline = android.os.SystemClock.elapsedRealtime() + 180_000
            var complete = false
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (!complete) {
                    if (android.os.SystemClock.elapsedRealtime() >= deadline) {
                        message = cloudText(R.string.kugou_qr_expired)
                        bitmap = null
                        complete = true
                        break
                    }
                    val result = client.checkQr(key)
                    when (result.status) {
                        4 -> {
                            complete = true
                            message = cloudText(R.string.kugou_login_success)
                            result.account?.let(currentOnLogin)
                        }
                        0, 800 -> {
                            complete = true
                            bitmap = null
                            message = cloudText(R.string.kugou_qr_expired)
                        }
                        2, 803 -> message = cloudText(R.string.kugou_qr_confirm)
                        1 -> message = cloudText(R.string.kugou_qr_wait)
                        else -> {
                            complete = true
                            message = cloudText(R.string.kugou_qr_status_error)
                        }
                    }
                    if (!complete) delay(2500)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loading = false
            message = cloudText(R.string.kugou_qr_network_error)
        }
    }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.kugou_qr_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.kugou_qr_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (loading) CircularProgressIndicator()
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                stringResource(R.string.kugou_qr_description),
                Modifier.sizeIn(maxWidth = 280.dp).fillMaxWidth().aspectRatio(1f),
            )
        }
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { revision++ },
                enabled = !loading,
            ) { Text(stringResource(R.string.kugou_qr_refresh)) }
            Button(enabled = bitmap != null && !saving, onClick = {
                val image = bitmap ?: return@Button
                saving = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val resolver = context.contentResolver
                            val uri =
                                checkNotNull(
                                    resolver.insert(
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        ContentValues().apply {
                                            put(MediaStore.Images.Media.DISPLAY_NAME, "kugou-login-${System.currentTimeMillis()}.png")
                                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SPICaMusic")
                                            put(MediaStore.Images.Media.IS_PENDING, 1)
                                        },
                                    ),
                                )
                            try {
                                checkNotNull(
                                    resolver.openOutputStream(uri),
                                ).use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                            } catch (error: Exception) {
                                resolver.delete(uri, null, null)
                                throw error
                            }
                        }
                        saved = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        message = cloudText(R.string.kugou_qr_save_error)
                    } finally {
                        saving = false
                    }
                }
            }) {
                Text(
                    if (saved) {
                        stringResource(
                            R.string.kugou_qr_saved_again,
                        )
                    } else {
                        stringResource(R.string.kugou_qr_save)
                    },
                )
            }
        }
        if (saved) Text(stringResource(R.string.kugou_qr_saved), color = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.kugou_qr_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        onCancel?.let { TextButton(onClick = it) { Text(stringResource(R.string.cloud_back_account)) } }
    }
}

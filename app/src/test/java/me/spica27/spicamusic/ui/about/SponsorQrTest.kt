package me.spica27.spicamusic.ui.about

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Test

class SponsorQrTest {
    @Test fun darkThemeQrDecodesToTheExactDonationAddress() {
        sponsorWallets.forEach { wallet ->
            val matrix = QRCodeWriter().encode(wallet.address, BarcodeFormat.QR_CODE, 320, 320)
            val pixels =
                IntArray(matrix.width * matrix.height) { index ->
                    if (matrix[index % matrix.width, index / matrix.width]) 0xffdce5e6.toInt() else 0xff141b1c.toInt()
                }
            val source = RGBLuminanceSource(matrix.width, matrix.height, pixels)
            val bitmap = BinaryBitmap(HybridBinarizer(source.invert()))
            assertEquals(wallet.network, wallet.address, QRCodeReader().decode(bitmap).text)
        }
    }

    @Test fun everyQrDecodesToTheExactDonationAddress() {
        sponsorWallets.forEach { wallet ->
            val matrix = QRCodeWriter().encode(wallet.address, BarcodeFormat.QR_CODE, 320, 320)
            val pixels =
                IntArray(
                    matrix.width * matrix.height,
                ) { index -> if (matrix[index % matrix.width, index / matrix.width]) 0xff000000.toInt() else 0xffffffff.toInt() }
            val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels)))
            assertEquals(wallet.network, wallet.address, QRCodeReader().decode(bitmap).text)
        }
    }
}

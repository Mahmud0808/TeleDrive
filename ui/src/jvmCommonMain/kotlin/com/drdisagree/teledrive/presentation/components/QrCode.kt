package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.ContentScale
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders [content] as a scannable QR code.
 *
 * The modules are drawn black on white rather than in scheme colors: scanners
 * expect dark-on-light and many reject an inverted code, so contrast here is a
 * functional requirement rather than a styling choice. The surface behind it
 * supplies the themed frame.
 */
@Composable
fun QrCode(content: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val image: ImageBitmap? = remember(content) { encodeQr(content) }
    if (image == null) return
    Image(
        bitmap = image,
        contentDescription = contentDescription,
        filterQuality = FilterQuality.None,
        contentScale = ContentScale.Fit,
        modifier = modifier.aspectRatio(1f)
    )
}

private fun encodeQr(content: String): ImageBitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to QR_MARGIN
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints)
    val image = ImageBitmap(matrix.width, matrix.height)
    val canvas = Canvas(image)
    val white = Paint().apply { color = Color.White }
    val black = Paint().apply { color = Color.Black }
    canvas.drawRect(0f, 0f, matrix.width.toFloat(), matrix.height.toFloat(), white)
    val module = Size(1f, 1f)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            if (matrix.get(x, y)) {
                canvas.drawRect(
                    x.toFloat(),
                    y.toFloat(),
                    x + module.width,
                    y + module.height,
                    black
                )
            }
        }
    }
    image
}.getOrNull()

private const val QR_SIZE = 512
private const val QR_MARGIN = 1

package com.btgun.desktop.pairing

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.client.j2se.MatrixToImageWriter
import java.awt.image.BufferedImage

object QrCodeRenderer {
    fun render(contents: String, size: Int = 240): BufferedImage {
        require(contents.isNotBlank()) { "contents must not be blank" }
        require(size >= 240) { "size must be at least 240" }

        val matrix = QRCodeWriter().encode(
            contents,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
            ),
        )
        return MatrixToImageWriter.toBufferedImage(matrix)
    }
}

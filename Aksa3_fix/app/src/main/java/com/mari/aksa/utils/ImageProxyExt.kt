// com/mari/aksa/utils/ImageProxyExt.kt
package com.mari.aksa.utils

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Convert ImageProxy (OUTPUT_IMAGE_FORMAT_RGBA_8888) -> Bitmap,
 * handling rowStride/pixelStride, lalu apply rotate+mirror.
 */
@SuppressLint("UseKtx")
fun ImageProxy.toBitmapCorrected(
    rotationDeg: Int,
    mirrorHorizontally: Boolean
): Bitmap {
    // Pastikan analyzer pakai RGBA_8888:
    // ImageAnalysis.Builder().setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
    val plane = planes[0]
    val rowStride = plane.rowStride         // bisa > width*4
    val pixelStride = plane.pixelStride     // biasanya 4 (RGBA)
    val w = width
    val h = height

    // Salin per-baris agar aman terhadap padding rowStride.
    val srcBuf = plane.buffer.duplicate().rewindAsReadOnly()
    val rowBytes = ByteArray(rowStride)
    val out = ByteArray(w * h * 4)

    var dst = 0
    for (y in 0 until h) {
        srcBuf.get(rowBytes, 0, rowStride) // ambil 1 baris RGBA (dengan padding)
        var src = 0
        for (x in 0 until w) {
            val base = src
            // RGBA
            out[dst]     = rowBytes[base]       // R
            out[dst + 1] = rowBytes[base + 1]   // G
            out[dst + 2] = rowBytes[base + 2]   // B
            out[dst + 3] = rowBytes[base + 3]   // A
            dst += 4
            src += pixelStride
        }
    }

    val bmp = Bitmap.createBitmap(w, h, Config.ARGB_8888)
    bmp.copyPixelsFromBuffer(ByteBuffer.wrap(out))

    // Rotasi + mirror (kamera depan)
    if (rotationDeg != 0 || mirrorHorizontally) {
        val m = Matrix().apply {
            if (mirrorHorizontally) postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f)
            if (rotationDeg != 0) postRotate(rotationDeg.toFloat(), bmp.width / 2f, bmp.height / 2f)
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
    return bmp
}

/** Small helper: rewind buffer safely as read-only */
private fun ByteBuffer.rewindAsReadOnly(): ByteBuffer =
    this.asReadOnlyBuffer().apply { rewind() }

package com.mari.aksa.utils


import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.graphics.get
import com.mari.aksa.ModelConstants
import androidx.core.graphics.createBitmap

class ImageUtils {
    companion object {
        fun convertBitmapToArray(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
            val width = bitmap.width
            val height = bitmap.height

            val output = Array(1) {
                Array(height) {
                    Array(width) {
                        FloatArray(3)
                    }
                }
            }
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = bitmap[x, y]
                    output[0][y][x][0] = pixel.red / 255f
                    output[0][y][x][1] = pixel.green / 255f
                    output[0][y][x][2] = pixel.blue / 255f
                }
            }

            return output
        }


        // ImageUtils.kt
        @SuppressLint("UseKtx")
        fun preprocessImageSquare640(originImage: Bitmap): Bitmap {
            val inputSize = ModelConstants.INPUT_SIZE // 640
            if (originImage.width == inputSize && originImage.height == inputSize) return originImage

            val scale = inputSize.toFloat() / maxOf(originImage.width, originImage.height)
            val newW = (originImage.width * scale).toInt().coerceAtLeast(1)
            val newH = (originImage.height * scale).toInt().coerceAtLeast(1)

            val resized = Bitmap.createScaledBitmap(originImage, newW, newH, true)
            val output = createBitmap(inputSize, inputSize)
            val c = android.graphics.Canvas(output)
            // letterbox hitam (default)
            c.drawColor(android.graphics.Color.BLACK)
            val dx = (inputSize - newW) / 2f
            val dy = (inputSize - newH) / 2f
            c.drawBitmap(resized, dx, dy, null)
            return output
        }


    }
}
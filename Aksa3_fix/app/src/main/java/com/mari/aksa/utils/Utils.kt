package com.mari.aksa.utils

import android.graphics.Bitmap
import android.util.Log
import com.mari.aksa.CocoClassNames
import com.mari.aksa.ModelConstants
import org.tensorflow.lite.Interpreter
import java.io.IOException

class Utils {
    companion object {

        // ==== UPDATE: rows dinamis: 4 (xywh) + numClasses ====
        private fun createOutputPlaceholder(): Array<Array<FloatArray>> {
            val rows = 4 + CocoClassNames.VALUES.size
            val cols = 8400
            return Array(1) {
                Array(rows) {
                    FloatArray(cols) { 0f }
                }
            }
        }

        fun runInference(
            tflite: Interpreter?,
            bitmap: Bitmap,
            logName: String,
        ): List<DetectionBox> {
            val detectionBoxes = mutableListOf<DetectionBox>()
            try {
                val inputSize = ModelConstants.INPUT_SIZE
                val feed = if (bitmap.width == inputSize && bitmap.height == inputSize) {
                    bitmap                              // sudah 640×640: pakai apa adanya
                } else {
                    ImageUtils.preprocessImageSquare640(bitmap) // baru preprocess kalau belum 640
                }
                val input = ImageUtils.convertBitmapToArray(feed)

                val output = createOutputPlaceholder()

                tflite?.run(input, output)

                detectionBoxes += YoloPostProcessor.getDetectionBoxes(
                    output = output,
                    imageWidth = ModelConstants.INPUT_SIZE.toDouble(),
                    imageHeight = ModelConstants.INPUT_SIZE.toDouble(),
                    inputSize = ModelConstants.INPUT_SIZE.toDouble()
                )

                detectionBoxes.forEach { box ->
                    Log.d(logName, "Detected: ${box.label}, conf=${"%.3f".format(box.confidence)} " +
                            "bbox=[${box.x}, ${box.y}, ${box.width}, ${box.height}]")
                }
            } catch (e: IOException) {
                Log.e(logName, "Inference failed: ${e.message}")
            }
            return detectionBoxes
        }


    }
}

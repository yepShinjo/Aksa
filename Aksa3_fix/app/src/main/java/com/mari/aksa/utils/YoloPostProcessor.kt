package com.mari.aksa.utils

import android.os.Parcelable
import android.util.Log
import com.mari.aksa.CocoClassNames
import kotlinx.android.parcel.Parcelize

@Suppress("DEPRECATED_ANNOTATION")
@Parcelize
data class DetectionBox(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val label: String,
    val confidence: Double,
    val score: Double = 1.0
) : Parcelable

class YoloPostProcessor {
    companion object {

        private fun customNms(
            boxes: List<List<Double>>,
            scores: List<Double>,
            confThreshold: Double,
            iouThreshold: Double
        ): List<Int> {
            val valid = scores.mapIndexedNotNull { i, s -> if (s >= confThreshold) i else null }
            if (valid.isEmpty()) return emptyList()

            val indicesSorted = valid.sortedByDescending { scores[it] }
            val keep = mutableListOf<Int>()

            var curIdx = indicesSorted
            var curBoxes = indicesSorted.map { boxes[it] }

            while (curIdx.isNotEmpty()) {
                val top = curIdx.first()
                keep += top
                if (curIdx.size == 1) break

                val topBox = curBoxes.first()
                val othersB = curBoxes.drop(1)
                val othersI = curIdx.drop(1)

                val next = mutableListOf<Int>()
                val nextBoxes = mutableListOf<List<Double>>()
                for (i in othersB.indices) {
                    val b = othersB[i]
                    val x1 = maxOf(topBox[0], b[0]); val y1 = maxOf(topBox[1], b[1])
                    val x2 = minOf(topBox[2], b[2]); val y2 = minOf(topBox[3], b[3])
                    val w = maxOf(0.0, x2 - x1); val h = maxOf(0.0, y2 - y1)
                    val inter = w * h
                    val areaTop = (topBox[2]-topBox[0]) * (topBox[3]-topBox[1])
                    val areaB = (b[2]-b[0]) * (b[3]-b[1])
                    val union = areaTop + areaB - inter
                    val iou = if (union > 0) inter / union else 0.0
                    if (iou <= iouThreshold) {
                        next += othersI[i]
                        nextBoxes += othersB[i]
                    }
                }
                curIdx = next
                curBoxes = nextBoxes
            }
            return keep
        }

        private fun postprocessOutput(
            output: Array<Array<FloatArray>>,
            confThreshold: Double,
            iouThreshold: Double,
            numClasses: Int
        ): Triple<List<List<Double>>, List<Double>, List<Int>> {
            // output shape dari TFLite: [1, rows, 8400]
            // rows = 4 (xywh) + numClasses
            val rows = 4 + numClasses
            if (output[0].size != rows) {
                Log.w("YoloPP", "Row count mismatch: got=${output[0].size} expected=$rows")
            }

            // Transpose [1, rows, 8400] -> [1, 8400, rows]
            val transposed = Array(1) { Array(output[0][0].size) { FloatArray(output[0].size) } }
            for (i in output[0][0].indices) {
                for (j in output[0].indices) {
                    transposed[0][i][j] = output[0][j][i]
                }
            }

            val boxes = mutableListOf<List<Double>>()
            val scores = mutableListOf<Double>()
            val classes = mutableListOf<Int>()

            for (det in transposed[0]) {
                // kelas mulai dari index 4 s/d 4+numClasses-1
                var bestClass = 0
                var bestProb = det[4]
                for (i in 1 until numClasses) {
                    val p = det[4 + i]
                    if (p > bestProb) {
                        bestProb = p
                        bestClass = i
                    }
                }
                val confidence = bestProb.toDouble()
                if (confidence >= confThreshold) {
                    val cx = det[0].toDouble()
                    val cy = det[1].toDouble()
                    val w  = det[2].toDouble()
                    val h  = det[3].toDouble()

                    val xMin = (cx - w/2) * 640
                    val yMin = (cy - h/2) * 640
                    val xMax = (cx + w/2) * 640
                    val yMax = (cy + h/2) * 640

                    boxes += listOf(xMin, yMin, xMax, yMax)
                    scores += confidence
                    classes += bestClass
                }
            }

            val keepIdx = customNms(boxes, scores, confThreshold, iouThreshold)
            return Triple(keepIdx.map { boxes[it] }, keepIdx.map { scores[it] }, keepIdx.map { classes[it] })
        }

        fun getDetectionBoxes(
            output: Array<Array<FloatArray>>,
            imageWidth: Double,
            imageHeight: Double,
            inputSize: Double,
            confThreshold: Double = 0.25,
            iouThreshold: Double = 0.45,
        ): List<DetectionBox> {
            val numClasses = CocoClassNames.VALUES.size
            val (detBoxes, detScores, detClasses) =
                postprocessOutput(output, confThreshold, iouThreshold, numClasses)

            val scaleX = imageWidth / inputSize
            val scaleY = imageHeight / inputSize

            val out = mutableListOf<DetectionBox>()
            for (i in detBoxes.indices) {
                val b = detBoxes[i]
                val xMin = b[0] * scaleX
                val yMin = b[1] * scaleY
                val xMax = b[2] * scaleX
                val yMax = b[3] * scaleY

                val detectedClass = detClasses[i]
                val label = CocoClassNames.VALUES.getOrElse(detectedClass) { "Unknown" }
                val box = DetectionBox(
                    x = xMin,
                    y = yMin,
                    width = (xMax - xMin),
                    height = (yMax - yMin),
                    label = label,
                    confidence = detScores[i]
                )
                out += box
            }
            return out
        }
    }
}
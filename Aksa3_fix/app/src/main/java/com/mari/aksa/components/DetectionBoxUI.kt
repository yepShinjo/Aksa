// com/mari/aksa/components/DetectionBoxUI.kt
package com.mari.aksa.components

import android.annotation.SuppressLint
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.mari.aksa.utils.DetectionBox
import com.mari.aksa.translit.PyPipeline

@SuppressLint("UseKtx")
@Composable
fun DetectionBoxUI(
    detectionBoxes: List<DetectionBox>,   // list hasil deteksi YOLO
    boxWidth: Float,                      // ukuran gambar asli YOLO (width)
    boxHeight: Float,                     // ukuran gambar asli YOLO (height)
    scale: Float = 1f,                    // faktor scaling tambahan
    textSizeSp: Float = 35f,              // ukuran teks transliterasi
    panelOpacity: Int = 185               // opasitas background panel translit
) {

    // Daftar huruf dasar aksara Bali (untuk mencari posisi rata-rata baris)
    val baseLabels = setOf(
        "ha","na","ca","ra","ka","da","ta","sa","wa","la",
        "ma","ga","ba","nga","pa","ja","ya","nya"
    )

    // 1. Ambil hasil grouping baris dari pipeline
    val uiLines   = PyPipeline.groupLinesForUi(detectionBoxes)

    // Transliterate semua baris → menjadi teks latin
    val lineTexts = PyPipeline.transliterate(detectionBoxes)

    // Pastikan jumlah baris = jumlah teks untuk menghindari error
    val count = minOf(uiLines.size, lineTexts.size)

    // Canvas = tempat kita menggambar manual (kotak, background, teks)
    Canvas(modifier = Modifier.fillMaxSize()) {

        drawIntoCanvas { canvas ->

            // Paint untuk teks transliterasi
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(220, 255, 220) // warna hijau terang
                textSize = textSizeSp                               // ukuran teks
                textAlign = Paint.Align.LEFT                        // rata kiri
                isFakeBoldText = true                               // dibuat lebih tebal
            }

            // Paint untuk background UI
            val bgPaint = Paint().apply {
                color = android.graphics.Color.argb(panelOpacity, 30, 30, 30) // transparan gelap
                style = Paint.Style.FILL
            }

            // Hitung scaling dari ukuran real → ke layar
            val sx = (size.width / boxWidth) * scale    // skala X
            val sy = (size.height / boxHeight) * scale  // skala Y

            // Padding panel teks
            val paddingX = 24f
            val paddingY = 18f

            // Batas minimal supaya panel tidak terlalu atas
            val minTop   = 64f

            // Loop setiap baris aksara Bali yang terdeteksi
            for (idx in 0 until count) {

                val line = uiLines[idx].members    // list box dalam baris ini
                val text = lineTexts[idx]          // transliterasi baris ini

                // Cari HURUF DASAR dalam baris ini (basis menentukan letak panel)
                val bases = line.filter { it.label in baseLabels }

                // Kalau tidak ada huruf dasar, gunakan semua huruf di baris
                val xs = bases.ifEmpty { line }

                // Cari kotak paling kiri dan paling kanan
                val leftMost  = xs.minByOrNull { it.x }
                val rightMost = xs.maxByOrNull { it.x + it.width }

                // Hitung posisi kiri & kanan background panel di layar (sudah di-scale)
                val bgLeft  = ((leftMost?.x ?: 0.0) * sx).toFloat() - paddingX
                val bgRight = (((rightMost?.x ?: 0.0) + (rightMost?.width ?: 0.0)) * sx).toFloat() + paddingX

                // Lebar panel minimal agar teks tidak terlalu mepet
                val maxWidthPx = (bgRight - bgLeft).coerceAtLeast(180f)


                // Hitung posisi Y panel berdasarkan rata-rata tinggi huruf dasar
                val anchorY = xs.map { it.y + it.height / 2.0 }.average().toFloat() * sy


                // Setup layout teks (StaticLayout)
                // Untuk menggambar teks multi-style di canvas
                val layout = StaticLayout.Builder
                    .obtain(text, 0, text.length, tp, maxWidthPx.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_CENTER) // teks di tengah
                    .setIncludePad(false)
                    .setMaxLines(1)
                    .build()

                // Hitung posisi atas panel (dengan minimal batas)
                val top  = (anchorY - layout.height / 2f).coerceAtLeast(minTop)

                // Posisi background panel
                val bgTop = top - paddingY
                val bgBottom = top + layout.height + paddingY

                // Gambar background panel
                canvas.nativeCanvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, bgPaint)

                // Gambar teks transliterasi
                canvas.nativeCanvas.save()

                // Posisi teks dimulai dari sini
                val startX = bgLeft + (bgRight - bgLeft - maxWidthPx) / 2f

                // Geser canvas ke posisi teks
                canvas.nativeCanvas.translate(startX, top)

                // Gambar teks
                layout.draw(canvas.nativeCanvas)

                canvas.nativeCanvas.restore()
            }
        }
    }
}

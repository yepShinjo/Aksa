package com.mari.aksa.translit

import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

data class Det(
    val label: String,   // nama label (misalnya "ha", "suku", "taleng")
    val role: Role,      // peran (BASE, VOWEL, GANTUNGAN, TOP_SIGN, END_SIGN, NUMBER, dsb)
    val x: Double, val y: Double, // koordinat pojok kiri-atas
    val w: Double, val h: Double, // lebar & tinggi kotak
    val conf: Double               // confidence dari model
) {
    // Titik tengah kotak (berguna untuk hitung jarak)
    val cx get() = x + w/2.0
    val cy get() = y + h/2.0
}

// Cek apakah posisi modifier (mod) wajar relatif terhadap base.
internal fun geometryOk(base: Det, mod: Det): Boolean {
    val bx = base.x; val by = base.y; val bw = base.w; val bh = base.h
    val mcx = mod.cx; val mcy = mod.cy
    val dx = abs(mcx - (bx + bw/2.0))
    val dy = abs(mcy - (by + bh/2.0))

    return when (mod.role) {
        // Vokal dengan aturan posisi khusus
        Role.VOWEL -> when (mod.label) {
            "ulu"   -> mod.cy <= base.y + 0.60*base.h && dx <= 1.30*base.w
            "pepet" -> mod.cy <= base.y + 0.75*base.h && dx <= 1.30*base.w
            "suku"  -> (base.y + 0.65*base.h) <= mod.cy &&
                    mod.cy <= (base.y + 2.10*base.h) && dx <= 0.95*base.w
            "taleng"-> (mod.cx <= base.cx) && dy <= 0.65*base.h && dx <= 1.30*base.w
            "tedong"-> (mod.cx >= base.cx) && dy <= 0.75*base.h && dx <= 1.55*base.w
            else    -> true
        }

        // Gantungan di bawah base
        Role.GANTUNGAN -> when (mod.label) {
            // KHUSUS: gantungan-pa biasanya agak di kanan, tidak terlalu jauh di bawah
            "gantungan-pa" -> {
                val rightOfBase = mod.cx >= base.cx
                val yOk = mod.cy >= base.y + 0.55*base.h &&
                        mod.cy <= base.y + 1.60*base.h
                val xOk = (mod.cx - base.cx) <= 1.40*base.w
                rightOfBase && yOk && xOk
            }
            else -> {
                (mod.cy >= base.y + 0.68*base.h) &&
                        (mod.cy <= base.y + 2.50*base.h) &&
                        dx <= 1.25*base.w
            }
        }

        // Top sign lebih longgar
        Role.TOP_SIGN  -> mod.cy <= base.y + 0.55*base.h && dx <= 1.80*base.w

        // End sign cenderung ke kanan base
        Role.END_SIGN  -> (mod.cx >= base.cx) && dy <= 0.75*base.h && dx <= 1.50*base.w

        else -> true
    }
}

// Hitung skor keterkaitan (affinity) antara base & modifier.
// Semakin tinggi skor → semakin cocok si modifier dianggap menempel ke base tsb.
fun affinityScore(base: Det, mod: Det): Double {
    val bx = base.x; val by = base.y; val bw = base.w; val bh = base.h
    val nx = abs(mod.cx - (bx + bw / 2.0)) / max(1.0, bw) // jarak normalisasi horizontal
    val ny = abs(mod.cy - (by + bh / 2.0)) / max(1.0, bh) // jarak normalisasi vertikal

    // Fungsi overlap area (IoA simetris)
    fun ov(ax: Double, ay: Double, aw: Double, ah: Double,
           bx: Double, by: Double, bw: Double, bh: Double): Double {
        val x1 = max(ax, bx); val y1 = max(ay, by)
        val x2 = min(ax + aw, bx + bw); val y2 = min(ay + ah, by + bh)
        if (x2 <= x1 || y2 <= y1) return 0.0
        val inter = (x2 - x1) * (y2 - y1)
        val ova = inter / max(1.0, aw * ah)
        val ovb = inter / max(1.0, bw * bh)
        return 0.5 * (ova + ovb)
    }
    val ov = ov(base.x, base.y, base.w, base.h, mod.x, mod.y, mod.w, mod.h)

    val geoPass = geometryOk(base, mod)
    val geoW = if (geoPass) 1.0 else 0.35 // kalau gagal geometri → bobot diturunkan

    // Bonus awal berdasarkan confidence
    var bonus = 0.1 + (mod.conf - 0.5) * 0.4

    // Tambah bonus sesuai jenis modifier
    when (mod.role) {
        Role.VOWEL -> when (mod.label) {
            "ulu"   -> bonus += geoW * (0.9  + 0.6 * ov)
            "pepet" -> bonus += geoW * (0.7  + 0.6 * ov)
            "suku"  -> bonus += geoW * (1.05 + 0.5 * ov)
            "taleng"-> bonus += geoW * (1.35 + 0.6 * (1.0 - nx) + 0.4 * ov)
            "tedong"-> bonus += geoW * (1.35 + 0.6 * (1.0 - nx) + 0.4 * ov)
            else    -> {}
        }

        Role.GANTUNGAN -> {
            bonus += geoW * (1.10 + 0.45 * (1.0 - nx) + 0.45 * ov)

            if (mod.label == "gantungan-pa") {
                // dorong kuat kalau dia ada di KANAN base, penalti kalau di kiri
                val dir = if (mod.cx >= (base.x + base.w/2.0)) 1.0 else -1.0
                bonus += geoW * (0.35 * dir)
            }
        }

        Role.TOP_SIGN  -> bonus += geoW * (1.35 + 0.75 * (1.0 - nx) + 0.25 * ov)
        Role.END_SIGN  -> bonus += geoW * (0.70 + 0.35 * ov)
        else -> {}
    }

    // Penalti jarak dari pusat
    val distPenalty = (nx + ny) * (if (mod.role == Role.TOP_SIGN) 0.30 else 0.55)
    return bonus - distPenalty
}

// com/mari/aksa/translit/PyPipeline.kt
package com.mari.aksa.translit

import android.util.Log
import com.mari.aksa.utils.DetectionBox
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

object PyPipeline {

    // ==== CONFIG (kembali ke versi lama, lebih stabil) ====
    private const val BASE_CONF_TH = 0.45
    private const val MOD_CONF_TH  = 0.40
    private const val NUM_CONF_TH  = 0.30

    private const val Y_MERGE = 0.55
    private const val ABSORB_LOOSEN_FR = 0.70

    private const val GAP_BIG_FR = 1.30
    private const val GAP_VOWEL_TO_ONSET_FR = 1.05

    private const val DEMOTE_HA = true
    private const val DEMOTE_GL_SCORE_TH  = 1.15
    private const val DEMOTE_GL_HEIGHT_FR = 0.82
    private const val DEMOTE_GL_BELOW_FR  = 0.68

    private const val LINE_MIN_ATTACHED_RATIO = 0.25
    private const val LINE_MIN_MODS_PER_BASE  = 0.45
    private const val LINE_MIN_MED_BASE_CONF  = 0.52

    // ==== helpers ====
    private data class RBox(
        val label: String, val role: Role, val conf: Double,
        val x1: Double, val y1: Double, val x2: Double, val y2: Double
    ) {
        val w = x2 - x1
        val h = y2 - y1
        val cx = (x1 + x2) / 2.0
        val cy = (y1 + y2) / 2.0
        val root = if (label.startsWith("gantungan-")) label.removePrefix("gantungan-") else label
    }

    private fun okByRole(lbl: String, conf: Double): Boolean {
        val norm = BaliRules.normalizeLabel(lbl)
        val role = BaliRules.roleOf[norm] ?: Role.IGNORE

        val isGantunganJa = (norm == "gantungan-ja")

        val rawKey = lbl.lowercase()
            .trim()
            .replace(" ", "")
            .replace("_", "")

        val decorativeBase = rawKey in setOf(
            "ba-kembang", "bakembang",
            "ga-gara", "gagara",
            "na-rambat", "narambat",
            "pa-kapal", "pakapal",
            "ra-repa", "rarepa",
            "sa-saga", "sasaga",
            "sa-sapa", "sasapa",
            "ta-latik", "talatik",
            "ta-tawa", "tatawa",
            "ja-jera", "jajera",
            "le-lenga", "lelenga",
            "da-madu", "damadu"
        )

        // vokal mandiri → pakai ambang MOD (0.40)
        if (BaliRules.isIndependentVowel(norm)) {
            return conf >= MOD_CONF_TH
        }

        // BASE dekoratif boleh pakai ambang MOD_CONF_TH (lebih rendah)
        if (role == Role.BASE && decorativeBase) {
            return conf >= MOD_CONF_TH
        }

        return when (role) {
            // ❌ jangan bedakan 'ja' lagi, treat sama seperti base lain
            Role.BASE -> conf >= BASE_CONF_TH

            Role.VOWEL,
            Role.TOP_SIGN,
            Role.END_SIGN -> conf >= MOD_CONF_TH

            // gantungan masih boleh sedikit longgar
            Role.GANTUNGAN -> {
                val th = if (isGantunganJa) 0.40 else 0.42
                conf >= th
            }

            Role.NUMBER -> conf >= NUM_CONF_TH
            else -> false
        }
    }

    private fun iou(a: RBox, b: RBox): Double {
        val x1 = max(a.x1, b.x1); val y1 = max(a.y1, b.y1)
        val x2 = min(a.x2, b.x2); val y2 = min(a.y2, b.y2)
        if (x2 <= x1 || y2 <= y1) return 0.0
        val inter = (x2 - x1) * (y2 - y1)
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        return inter / max(1.0, areaA + areaB - inter)
    }

    private fun ioaSmall(a: RBox, b: RBox): Double {
        val x1 = max(a.x1, b.x1); val y1 = max(a.y1, b.y1)
        val x2 = min(a.x2, b.x2); val y2 = min(a.y2, b.y2)
        if (x2 <= x1 || y2 <= y1) return 0.0
        val inter = (x2 - x1) * (y2 - y1)
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        return inter / max(1.0, min(areaA, areaB))
    }

    // 1) SANITIZE (gantungan vs base duplikat + NMS per-role/identitas)
    private fun sanitize(
        raw: List<RBox>,
        overlapGate: Double = 0.35,
        nmsIou: Double = 0.60
    ): List<RBox> {

        val keep = BooleanArray(raw.size) { true }

        // a) prefer gantungan daripada BASE dengan root sama di band bawah
        for (i in raw.indices) {
            val a = raw[i]
            if (!keep[i] || a.role != Role.GANTUNGAN) continue

            for (j in raw.indices) {
                if (i == j || !keep[j]) continue
                val b = raw[j]
                if (b.role != Role.BASE) continue
                if (a.root != b.root) continue

                val ov = ioaSmall(a, b)
                if (ov < overlapGate) continue
                val inBand = (a.cy >= b.y1 + 0.60 * b.h) &&
                        (a.cy <= b.y1 + 2.20 * b.h)
                if (inBand && a.conf >= b.conf) {
                    // duplikat base, buang BASE-nya
                    keep[j] = false
                }
            }
        }

        // b) NMS: per-role dan per-identitas
        val order = raw.indices.filter { keep[it] }
            .sortedByDescending { raw[it].conf }
        val finalKeep = BooleanArray(raw.size)

        for (i in order) {
            if (!keep[i] || finalKeep[i]) continue
            finalKeep[i] = true

            for (j in order) {
                if (i == j || !keep[j] || finalKeep[j]) continue

                val ri = raw[i].role
                val rj = raw[j].role

                // angka tidak pernah di-suppress oleh non-angka
                if (ri == Role.NUMBER && rj != Role.NUMBER) continue
                if (rj == Role.NUMBER && ri != Role.NUMBER) continue

                // >>> bedakan identitas (label/root) seperti versi yang kamu mau
                val sameIdentity = when (ri) {
                    Role.GANTUNGAN ->
                        rj == Role.GANTUNGAN && raw[i].root == raw[j].root
                    Role.BASE ->
                        rj == Role.BASE && raw[i].label == raw[j].label
                    else ->
                        rj == ri && raw[i].label == raw[j].label
                }
                if (!sameIdentity) continue

                val indepI = BaliRules.isIndependentVowel(raw[i].label)
                val indepJ = BaliRules.isIndependentVowel(raw[j].label)

                val thr = when {
                    ri == Role.NUMBER -> 0.55          // rj pasti NUMBER juga kalau ri NUMBER
                    indepI || indepJ  -> 0.80
                    else              -> nmsIou
                }


                if (iou(raw[i], raw[j]) > thr) {
                    keep[j] = false
                }
            }
        }

        return raw.indices.filter { keep[it] }.map { raw[it] }
    }

    // 2) GROUP LINES (best-match + absorb + heal)
    private data class LineBox(val members: MutableList<RBox>)

    private fun inflatedBand(b: RBox): Pair<Double, Double> {
        return when (b.role) {
            Role.TOP_SIGN   -> b.y1 to (b.y2 + 0.55 * b.h)
            Role.GANTUNGAN  -> (b.y1 - 0.8 * b.h) to b.y2
            Role.VOWEL      ->
                if (b.label == "suku")
                    (b.y1 - 0.7 * b.h) to b.y2
                else
                    (b.y1 - 0.35 * b.h) to (b.y2 + 0.35 * b.h)
            Role.NUMBER     -> (b.y1 - 0.25 * b.h) to (b.y2 + 0.25 * b.h)
            else            -> (b.y1 - 0.25 * b.h) to (b.y2 + 0.25 * b.h)
        }
    }

    private fun groupLines(boxes: List<RBox>, yMerge: Double = Y_MERGE): List<LineBox> {
        if (boxes.isEmpty()) return emptyList()
        val bxs = boxes.sortedWith(compareBy<RBox> { it.cy }.thenBy { it.x1 }).toMutableList()

        fun bandOf(line: LineBox): Pair<Double, Double> {
            val lo = line.members.minOf { inflatedBand(it).first }
            val hi = line.members.maxOf { inflatedBand(it).second }
            return lo to hi
        }

        fun vfrac(b: RBox, lo: Double, hi: Double): Double {
            val lh = (hi - lo) + 1e-6
            val ov = max(0.0, min(b.y2, hi) - max(b.y1, lo))
            return ov / max(1.0, min(b.h, lh))
        }

        fun hasBase(line: LineBox) = line.members.any { it.role == Role.BASE }

        // pass 1: best-match
        val lines = mutableListOf<LineBox>()
        for (b in bxs) {
            var bestIdx = -1
            var bestScore = 0.0
            for (idx in lines.indices) {
                val (lo, hi) = bandOf(lines[idx])
                val frac = vfrac(b, lo, hi)
                if (frac <= yMerge) continue
                val score = frac + (if (b.role != Role.BASE && hasBase(lines[idx])) 0.12 else 0.0)
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = idx
                }
            }
            if (bestIdx >= 0) lines[bestIdx].members += b else lines += LineBox(mutableListOf(b))
        }

        // pass 2: absorb modifier-only lines → ke line base terdekat
        val baseIdxs = lines.indices.filter { hasBase(lines[it]) }
        if (baseIdxs.isNotEmpty()) {
            fun lineOverlap(i: Int, j: Int): Double {
                val (a1, a2) = bandOf(lines[i]); val (b1, b2) = bandOf(lines[j])
                val ov = max(0.0, min(a2, b2) - max(a1, b1))
                val denom = max(1.0, min(a2 - a1, b2 - b1))
                return ov / denom
            }
            for (i in lines.indices) {
                if (i in baseIdxs || !lines[i].members.any()) continue
                if (hasBase(lines[i])) continue
                var bestJ = -1; var bestF = 0.0
                for (j in baseIdxs) {
                    val f = lineOverlap(i, j)
                    if (f > bestF) {
                        bestF = f; bestJ = j
                    }
                }
                if (bestJ >= 0 && bestF > (yMerge * ABSORB_LOOSEN_FR)) {
                    lines[bestJ].members.addAll(lines[i].members)
                    lines[i].members.clear()
                }
            }
        }

        // pass 3: heal line kecil base-only → gabung ke line base utama
        val healed = lines.toMutableList()
        fun hasBase2(line: LineBox) = line.members.any { it.role == Role.BASE }

        fun bandOf2(line: LineBox): Pair<Double, Double> {
            val lo = line.members.minOf { inflatedBand(it).first }
            val hi = line.members.maxOf { inflatedBand(it).second }
            return lo to hi
        }

        fun lineOverlap2(i: Int, j: Int): Double {
            val (a1, a2) = bandOf2(healed[i]); val (b1, b2) = bandOf2(healed[j])
            val ov = max(0.0, min(a2, b2) - max(a1, b1))
            val denom = max(1.0, min(a2 - a1, b2 - b1))
            return ov / denom
        }

        for (i in healed.indices) {
            if (!hasBase2(healed[i])) continue
            val baseCnt = healed[i].members.count { it.role == Role.BASE }
            if (baseCnt in 1..2) {
                var bestJ = -1; var bestF = 0.0
                for (j in healed.indices) {
                    if (i == j || !hasBase2(healed[j])) continue
                    val f = lineOverlap2(i, j)
                    if (f > bestF) {
                        bestF = f; bestJ = j
                    }
                }
                if (bestJ >= 0 && bestF > (yMerge * 0.90)) {
                    healed[bestJ].members.addAll(healed[i].members)
                    healed[i].members.clear()
                }
            }
        }

        return healed.filter { it.members.isNotEmpty() }
            .onEach { line ->
                // Sorting stabil: BASE dulu, baru modifier, lalu posisi X/Y
                line.members.sortWith(
                    compareBy<RBox> { it.role.priority }
                        .thenBy { it.cx }
                        .thenBy { it.cy }
                )
            }
    }

    // 3) Line “Balinese enough” filter
    private fun balineseScore(members: List<RBox>): Triple<Double, Double, Double> {
        val bases = members.filter { it.role == Role.BASE }
        if (bases.isEmpty()) return Triple(0.0, 0.0, 0.0)
        var attached = 0
        var totalMods = 0
        val mods = members.filter { it.role in setOf(Role.VOWEL, Role.GANTUNGAN, Role.TOP_SIGN, Role.END_SIGN) }
        for (b in bases) {
            var cnt = 0
            for (m in mods) {
                if (geometryOk(b.toDet(), m.toDet()) && affinityScore(b.toDet(), m.toDet()) > 0.0) cnt++
            }
            if (cnt > 0) attached++
            totalMods += cnt
        }
        val ratio = attached / max(1.0, bases.size.toDouble())
        val mpb = totalMods / max(1.0, bases.size.toDouble())
        val medc = bases.map { it.conf }.sorted().let { s -> s[s.size / 2] }
        return Triple(ratio, mpb, medc)
    }

    private fun shouldKeepLine(members: List<RBox>): Boolean {
        val bases  = members.count { it.role == Role.BASE }
        val digits = members.count { it.role == Role.NUMBER }

        if (digits > 0) return true

        val (ratio, mpb, medc) = balineseScore(members)
        if (bases >= 3 && ratio < LINE_MIN_ATTACHED_RATIO && mpb < LINE_MIN_MODS_PER_BASE) return false
        if (medc < LINE_MIN_MED_BASE_CONF && ratio < (LINE_MIN_ATTACHED_RATIO + 0.10)) return false
        return true
    }

    // 4) Confusion repair: ha -> gantungan-la
    private fun recastHaToGantunganLa(dets: List<RBox>): List<RBox> {
        if (!DEMOTE_HA) return dets
        val bases = dets.filter { it.role == Role.BASE }
        if (bases.isEmpty()) return dets
        val medH = bases.map { it.h }.sorted().let { s -> s[s.size / 2] }
        val out = dets.toMutableList()
        for (i in out.indices) {
            val d = out[i]
            if (d.role != Role.BASE || d.label != "ha") continue
            var bestB: RBox? = null; var bestS = -1e9
            val pseudo = d.copy(role = Role.GANTUNGAN, label = "gantungan-la")
            for (b in bases) {
                if (b === d) continue
                if (!geometryOk(b.toDet(), pseudo.toDet())) continue
                val s = affinityScore(b.toDet(), pseudo.toDet())
                if (s > bestS) {
                    bestS = s; bestB = b
                }
            }
            if (bestB == null) continue
            val smallish = d.h <= DEMOTE_GL_HEIGHT_FR * medH
            val belowish = d.cy >= (bestB.y1 + DEMOTE_GL_BELOW_FR * bestB.h)
            val hasNearLa = out.any { m ->
                m !== d && m.role == Role.GANTUNGAN && m.root == "la" &&
                        abs(m.cx - d.cx) <= 0.6 * max(d.w, bestB.w) &&
                        abs(m.cy - d.cy) <= 0.9 * max(d.h, bestB.h)
            }
            if (bestS >= DEMOTE_GL_SCORE_TH && smallish && belowish && !hasNearLa) {
                out[i] = d.copy(role = Role.GANTUNGAN, label = "gantungan-la")
            }
        }
        return out
    }

    // 4b) Confusion repair: digit "3" -> o-kara
    private fun recastDigit3ToOkara(dets: List<RBox>): List<RBox> {
        val bases = dets.filter { it.role == Role.BASE }
        val digits = dets.filter { it.role == Role.NUMBER }

        if (digits.size >= 3 || bases.isEmpty()) return dets

        val baseHs = bases.map { it.h }.sorted()
        if (baseHs.isEmpty()) return dets
        val medBaseH = baseHs[baseHs.size / 2]

        val out = dets.toMutableList()
        for (i in out.indices) {
            val d = out[i]
            if (d.role != Role.NUMBER || d.label != "3") continue

            val hRatio = d.h / medBaseH
            val minCy = bases.minOf { it.cy }
            val maxCy = bases.maxOf { it.cy }
            val nearBaseline = d.cy >= (minCy - 0.20 * medBaseH) &&
                    d.cy <= (maxCy + 0.20 * medBaseH)

            if (hRatio in 0.80..1.30 && nearBaseline) {
                out[i] = d.copy(
                    label = "o-kara",
                    role = Role.BASE
                )
            }
        }
        return out
    }

    // 4c) Confusion repair gantungan-pa vs gantungan-sa pakai SUKU
    private fun recastGantunganPaSaBySuku(dets: List<RBox>): List<RBox> {
        val out = dets.toMutableList()

        val sukus = out.mapIndexedNotNull { idx, b ->
            if (b.role == Role.VOWEL && b.label == "suku") idx to b else null
        }
        if (sukus.isEmpty()) return dets

        val bases = out.mapIndexedNotNull { idx, b ->
            if (b.role == Role.BASE) idx to b else null
        }

        for (gIndex in out.indices) {
            val g = out[gIndex]
            if (g.role != Role.GANTUNGAN || g.label != "gantungan-pa") continue

            val prevPair = bases
                .filter { (_, b) ->
                    b.x2 <= g.x1 &&
                            abs(b.cy - g.cy) <= 0.9 * max(b.h, g.h)
                }
                .maxByOrNull { (_, b) -> b.x2 }
                ?: continue

            val (_, prevBase) = prevPair

            val prevH = prevBase.h
            val xLeftPrev  = prevBase.x1 - 0.20 * prevH
            val xRightPrev = g.x1 + 0.35 * g.w
            val yTopPrev   = prevBase.y1 + 0.55 * prevH
            val yBotPrev   = prevBase.y1 + 2.30 * prevH

            val sukuUnderPrev = sukus.firstOrNull { (_, s) ->
                s.cx in xLeftPrev..xRightPrev &&
                        s.cy >= yTopPrev && s.cy <= yBotPrev
            }

            val gH = g.h
            val gxLeft  = g.x1
            val gxRight = g.x2
            val gyTop   = g.y1 + 0.55 * gH
            val gyBot   = g.y1 + 2.20 * gH

            val sukuUnderG = sukus.firstOrNull { (_, s) ->
                s.cx in gxLeft..gxRight &&
                        s.cy >= gyTop && s.cy <= gyBot
            }

            if (sukuUnderPrev != null && sukuUnderG == null) {
                val (sIdx, sBox) = sukuUnderPrev
                out[sIdx] = sBox.copy(role = Role.IGNORE)
                out[gIndex] = g.copy(label = "gantungan-sa")
            }
        }

        return out
    }

    // ==== helper baru: bereskan kasus ulu vs pepet dalam satu base ====
    private fun normalizeVowelsForBase(mods: List<RBox>): List<RBox> {
        val vowels = mods.filter { it.role == Role.VOWEL }
        if (vowels.isEmpty()) return mods

        // kadang YOLO detect ulu + pepet di satu titik
        val ulus = vowels.filter { it.label == "ulu" || it.label == "ulu-sari" }
        val pepets = vowels.filter { it.label == "pepet" }

        if (ulus.isNotEmpty() && pepets.isNotEmpty()) {
            val bestUlu = ulus.maxBy { it.conf }
            val bestPep = pepets.maxBy { it.conf }

            // kalau ulu tidak jauh lebih jelek dari pepet → paksa pilih ulu
            val dropLabel = if (bestUlu.conf >= bestPep.conf - 0.12) "pepet" else "ulu"

            return mods.filterNot { it.role == Role.VOWEL && it.label == dropLabel }
        }

        return mods
    }

    // 5) Transliteration per line
    private val pureOnsets = setOf(
        "b", "c", "d", "g", "h", "j", "k", "l", "m",
        "n", "ny", "p", "r", "s", "t", "w", "y", "ng"
    )

    private fun transliterateLine(members: List<RBox>): String {
        val dets0 = recastHaToGantunganLa(members)
        val dets1 = recastDigit3ToOkara(dets0)
        val dets = recastGantunganPaSaBySuku(dets1)

        val bases  = dets.filter { it.role == Role.BASE }
        val digits = dets.filter { it.role == Role.NUMBER }
        if (bases.isEmpty()) {
            return if (digits.size >= 3)
                digits.sortedBy { it.x1 }.joinToString("") { it.label }
            else ""
        }

        // 2) attach modifier ke base skor tertinggi
        // 2) attach modifier ke base
        val clusters = bases.associateWith { mutableListOf<RBox>() }.toMutableMap()
        for (d in dets) {
            if (d.role in setOf(Role.IGNORE, Role.BASE, Role.NUMBER)) continue

            var bestB: RBox? = null
            var bestS = -1e9

            // kandidat awal: berdasarkan affinity score (geometri + overlap)
            for (b in bases) {
                val s = affinityScore(b.toDet(), d.toDet())
                if (s > bestS) {
                    bestS = s
                    bestB = b
                }
            }

            // KHUSUS: gantungan-pa boleh "loncat" ke base kiri kalau lebih cocok
            if (bestB != null && d.label == "gantungan-pa") {
                if (d.cx < bestB.cx) {
                    val leftCandidate = bases
                        .filter { it.cx <= d.cx }
                        .maxByOrNull { it.cx }

                    if (leftCandidate != null) {
                        val sLeft = affinityScore(leftCandidate.toDet(), d.toDet())
                        if (sLeft >= (bestS - 0.25)) {
                            bestB = leftCandidate
                            bestS = sLeft
                        }
                    }
                }
            }

            // KHUSUS: pepet / ulu / taleng / tedong → pakai nearest-base by X
            val isTopVowel =
                d.role == Role.VOWEL && d.label in setOf("ulu", "pepet", "taleng", "tedong")
            if (isTopVowel) {
                val candidates = bases
                    .filter { geometryOk(it.toDet(), d.toDet()) }
                    .ifEmpty { bases }

                val nearestByX = candidates.minByOrNull { abs(it.cx - d.cx) }
                if (nearestByX != null) {
                    bestB = nearestByX
                }
            }

            // 💥 KHUSUS: END_SIGN (bisah) SELALU NEMPEL KE BASE DI KIRI
            if (d.role == Role.END_SIGN) {
                // cari base dengan cx <= posisi bisah, ambil yang paling dekat di kiri
                val leftBase = bases
                    .filter { it.cx <= d.cx }
                    .maxByOrNull { it.cx }

                if (leftBase != null) {
                    bestB = leftBase
                }
            }

            if (bestB != null && bestS > -1.0) {
                clusters.getValue(bestB).add(d)
            }
        }

        // 3) render cluster → token
        data class Tok(val text: String, val xl: Double, val xr: Double, val isDigit: Boolean)
        val tokens = mutableListOf<Tok>()
        val baseWs = bases.sortedBy { it.x1 }.map { it.w }

        for (b in bases.sortedBy { it.x1 }) {
            val rawMods = clusters[b] ?: emptyList()
            // >>> di sini kita bereskan kasus ulu vs pepet
            val mods = normalizeVowelsForBase(rawMods)
            val modLabels = mods.map { it.label }.toSet()

            // VOKAL MANDIRI
            if (BaliRules.isIndependentVowel(b.label)) {
                val core = BaliRules.latinIndependent[b.label] ?: ""
                val top  = BaliRules.suffixTop(modLabels)
                val end  = BaliRules.suffixEnd(modLabels)
                tokens += Tok(core + top + end, b.x1, b.x2, false)
                continue
            }

            val hasTaleng = "taleng" in modLabels
            val hasTedong = "tedong" in modLabels
            val killVowel = "adeg-adeg" in modLabels

            val gants = mods.filter { it.role == Role.GANTUNGAN }.sortedBy { it.x1 }
            val cons = mutableListOf(BaliRules.latinBase[b.label] ?: "")
            gants.forEach { cons += (BaliRules.latinGantungan[it.label] ?: "") }

            val onset   = cons.dropLast(1).joinToString("")
            val nucleus = cons.lastOrNull().orEmpty()
            val vowel   = if (killVowel) "" else BaliRules.vowelFrom(modLabels, hasTaleng, hasTedong)
            val coda    = BaliRules.suffixTop(modLabels) + BaliRules.suffixEnd(modLabels)

            tokens += Tok(onset + nucleus + vowel + coda, b.x1, b.x2, false)
        }

        digits.forEach { d -> tokens += Tok(d.label, d.x1, d.x2, true) }
        if (tokens.isEmpty()) return ""

        tokens.sortBy { it.xl }
        val avgBw = (if (baseWs.isNotEmpty()) baseWs.sorted()[baseWs.size / 2]
        else tokens.map { it.xr - it.xl }.sorted().let { it[it.size / 2] })
            .coerceAtLeast(1.0)

        val out = mutableListOf<String>()
        var prevRight: Double? = null
        for (t in tokens) {
            var gapSpace = false
            if (prevRight != null) {
                val gap = t.xl - prevRight
                val prevText = out.lastOrNull { it != " " }
                val prevIsDigit = prevText?.all { it.isDigit() } == true
                if (!(prevIsDigit && t.isDigit)) {
                    if (gap > GAP_BIG_FR * avgBw) {
                        gapSpace = true
                    } else if (prevText != null && !prevIsDigit) {
                        val prevEndsVowel = "aiueo".any { prevText.endsWith(it) }
                        val startsNewOnset = pureOnsets.any { t.text.startsWith(it) }
                        if (prevEndsVowel && startsNewOnset &&
                            gap > GAP_VOWEL_TO_ONSET_FR * avgBw
                        ) {
                            gapSpace = true
                        }
                    }
                }
            }
            if (gapSpace && (out.isEmpty() || out.last() != " ")) out += " "
            out += t.text
            prevRight = t.xr
        }

        val merged = mutableListOf<String>()
        var i = 0
        while (i < out.size) {
            if (out[i] == " ") { merged += " "; i++; continue }
            if (out[i].all { it.isDigit() }) { merged += out[i]; i++; continue }
            var j = i + 1
            while (j < out.size && out[j] == " ") j++
            if (j < out.size && !out[j].all { it.isDigit() }) {
                val cur = out[i]; val nxt = out[j]
                if (pureOnsets.contains(cur) && nxt.startsWith(cur)) {
                    i = j; continue
                }
            }
            merged += out[i]; i++
        }
        return merged.joinToString("").replace("  ", " ").trim()
    }

    // ==== Instrumentasi: hitung boks per tahap ====
    private fun countByStage(
        raw: List<DetectionBox>,
        gated: List<RBox>,
        cleaned: List<RBox>,
        lines: List<LineBox>,
        kept: List<LineBox>
    ): String {
        val rAll = raw.size
        val gAll = gated.size
        val cAll = cleaned.size
        val gBases = gated.count { it.role == Role.BASE }
        val cBases = cleaned.count { it.role == Role.BASE }
        val gMods  = gAll - gBases
        val cMods  = cAll - cBases
        val keptBoxes = kept.sumOf { it.members.size }
        return "raw=$rAll  gate=$gAll(b=$gBases,m=$gMods)  " +
                "sanit=$cAll(b=$cBases,m=$cMods)  lines=${lines.size}  kept=${kept.size}  keptBoxes=$keptBoxes"
    }

    // ==== PUBLIC API ==== //
    fun transliterate(dets: List<DetectionBox>): List<String> {
        val gated = dets.filter { okByRole(it.label, it.confidence) }
            .map {
                val lbl = BaliRules.normalizeLabel(it.label)
                val role = BaliRules.roleOf[lbl] ?: Role.IGNORE
                val x1 = it.x; val y1 = it.y
                val x2 = it.x + it.width; val y2 = it.y + it.height
                RBox(lbl, role, it.confidence, x1, y1, x2, y2)
            }

        val cleaned = sanitize(gated)
        val lines = groupLines(cleaned)
        val kept = lines.filter { shouldKeepLine(it.members) }

        Log.d("AksaPy", countByStage(dets, gated, cleaned, lines, kept))

        return kept
            .map { transliterateLine(it.members) }
            .filter { it.isNotBlank() }
    }

    data class UiLine(val members: List<DetectionBox>)

    fun groupLinesForUi(dets: List<DetectionBox>): List<UiLine> {
        val gated = dets.filter { okByRole(it.label, it.confidence) }
            .map {
                val lbl = BaliRules.normalizeLabel(it.label)
                val role = BaliRules.roleOf[lbl] ?: Role.IGNORE
                val x1 = it.x; val y1 = it.y
                val x2 = it.x + it.width; val y2 = it.y + it.height
                RBox(lbl, role, it.confidence, x1, y1, x2, y2)
            }
        val cleaned = sanitize(gated)
        val lines = groupLines(cleaned)
        val kept = lines.filter { shouldKeepLine(it.members) }

        Log.d("AksaPy", "UI " + countByStage(dets, gated, cleaned, lines, kept))

        return kept.map { line ->
            val mem = line.members.sortedBy { it.x1 }.map { r ->
                DetectionBox(
                    x = r.x1, y = r.y1,
                    width = r.w, height = r.h,
                    label = r.label,
                    confidence = r.conf,
                    score = 1.0
                )
            }
            UiLine(mem)
        }
    }

    private fun RBox.toDet() = Det(label, role, x1, y1, w, h, conf)
}
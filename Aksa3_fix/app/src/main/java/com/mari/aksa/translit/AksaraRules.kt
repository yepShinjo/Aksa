// com/mari/aksa/translit/BaliRules.kt
package com.mari.aksa.translit

// Role = peran simbol dalam satu suku kata/baris
// BASE       : aksara dasar (ha, na, ca, ...)
// GANTUNGAN  : konsonan bawah (subjoined) yang menempel ke BASE
// VOWEL      : tanda vokal samping (ulu, suku, pepet, taleng, tedong)
// TOP_SIGN   : tanda atas (surang=r, cecek=ng)
// END_SIGN   : tanda di akhir (adeg-adeg, bisah=h)
// NUMBER     : angka bali
// IGNORE     : simbol yang diabaikan saat penyusunan (contoh: sentinel "end")
enum class Role {
    BASE, GANTUNGAN, VOWEL, TOP_SIGN, END_SIGN, NUMBER, IGNORE;

    // Dipakai untuk sorting stabil: base dulu, lalu modifier
    val priority: Int
        get() = when (this) {
            BASE -> 0
            GANTUNGAN, VOWEL -> 1
            TOP_SIGN -> 2
            END_SIGN -> 3
            NUMBER -> 4
            IGNORE -> 5
        }
}


object BaliRules {

    // ===== Alias normalisasi label =====
    // Tujuan:
    // 1) Samakan berbagai variasi penulisan ke bentuk yang dipakai engine (snake/space → dihapus, case → lowercase).
    // 2) Gabungkan sebutan lokal/typo model menjadi label baku (misal "surang-r-" → "surang").
    // Catatan: kunci alias DIHARUSKAN lowercase TANPA spasi/underscore.
    // ===== Alias normalisasi label =====
    private val alias = mapOf(
        "ba-kembang" to "ba","ga-gara" to "ga","na-rambat" to "na","pa-kapal" to "pa",
        "ra-repa" to "ra".lowercase(),
        "sa-saga" to "sa","sa-sapa" to "sa","ta-latik" to "ta","ta-tawa" to "ta",
        "ja-jera" to "ja","le-lenga" to "la", "da-madu"   to "da",
        "cecek(ng)" to "cecek","surang(r)" to "surang","surang-r-" to "surang",
        "bisah(h)" to "bisah",
        "cecek-ng" to "cecek","cecek-ng-" to "cecek",

        // variasi penulisan vokal mandiri (tanpa tanda minus / pakai underscore)
        "akara" to "a-kara",
        "ikara" to "i-kara",
        "ukara" to "u-kara",
        "ekara" to "e-kara",
        "okara" to "o-kara",

        // variasi gantungan yang sering jadi "snake_case"
        "bo" to "ba",
        "gantunganbo" to "gantungan-ba",
        "gantunganja" to "gantungan-ja",
        "gantunganpa" to "gantungan-pa",
        "gantungansa" to "gantungan-sa",
        "gantunganka" to "gantungan-ka",

        "bisah-h-" to "bisah"
    )

    // ===== Vokal mandiri =====
    // Catatan penting: vokal mandiri TIDAK dinormalisasi jadi 'a','i','u','e','o' secara generik,
    // karena mereka punya status aksara sendiri (dipetakan eksplisit via latinIndependent).
    private val independentVowels: Set<String> =
        setOf("a-kara","i-kara","u-kara","e-kara","o-kara")

    fun isIndependentVowel(lbl: String): Boolean =
        independentVowels.contains(lbl)

    // ===== Normalisasi label deteksi mentah =====
    // Langkah:
    // - lowercase
    // - trim
    // - hapus spasi & underscore
    // - tangani pola khusus "cecek" yang ada "-ng" nya
    // - cek di alias map → kalau ada, ganti
    fun normalizeLabel(raw: String): String {
        val t = raw.lowercase().trim().replace(" ", "").replace("_", "")
        if (t.startsWith("cecek") && t.contains("ng")) return "cecek" // satukan variasi cecek-ng
        return alias[t] ?: t
    }

    // ===== Pemetaan label → Role =====
    // Dipakai engine penyusun suku kata untuk menentukan bagaimana simbol berinteraksi.
    val roleOf: Map<String, Role> = buildMap {
        // BASE (aksara dasar)
        listOf(
            "ha","na","ca","ra","ka","da","ta","sa","wa","la",
            "ma","ga","ba","nga","pa","ja","ya","nya"
        ).forEach { put(it, Role.BASE) }

        // GANTUNGAN (konsonan bawah / subjoined)
        listOf(
            "gantungan-ba","gantungan-ca","gantungan-da","gantungan-ga","gantungan-ha",
            "gantungan-ja","gantungan-ka","gantungan-la","gantungan-ma","gantungan-na",
            "gantungan-nya","gantungan-pa","gantungan-ra","gantungan-sa","gantungan-ta",
            "gantungan-wa","gantungan-ya"
        ).forEach { put(it, Role.GANTUNGAN) }

        // Vokal mandiri → diperlakukan sebagai BASE agar tidak ke-drop saat penyusunan
        independentVowels.forEach { put(it, Role.BASE) }

        // Tanda vokal samping (modifier vokal)
        listOf("ulu","suku","pepet","taleng","tedong").forEach { put(it, Role.VOWEL) }
        // Tanda atas (surang=r; cecek=ng)
        listOf("surang","cecek").forEach { put(it, Role.TOP_SIGN) }
        // Tanda akhir kata/suku (adeg-adeg, bisah=h)
        listOf("adeg-adeg","bisah").forEach { put(it, Role.END_SIGN) }

        // Angka bali
        listOf("0","1","2","3","4","5","6","7","8","9").forEach { put(it, Role.NUMBER) }

        // Sentinel yang diabaikan
        listOf("end").forEach { put(it, Role.IGNORE) }
    }

    // ===== Pemetaan Latin untuk vokal mandiri =====
    // Dipakai saat simbolnya adalah vokal mandiri (bukan kombinasi BASE+VOWEL).
    val latinIndependent = mapOf(
        "a-kara" to "a",
        "i-kara" to "i",
        "u-kara" to "u",
        "e-kara" to "e",
        "o-kara" to "o"
    )

    // ===== Pemetaan Latin untuk BASE & GANTUNGAN =====
    // BASE → konsonan dasar; GANTUNGAN → konsonan bawah (subjoined) yang melebur
    val latinBase = mapOf(
        "ha" to "h","na" to "n","ca" to "c","ra" to "r","ka" to "k","da" to "d","ta" to "t","sa" to "s","wa" to "w","la" to "l",
        "ma" to "m","ga" to "g","ba" to "b","nga" to "ng","pa" to "p","ja" to "j","ya" to "y","nya" to "ny"
    )
    val latinGantungan = mapOf(
        "gantungan-ba" to "b","gantungan-ca" to "c","gantungan-da" to "d","gantungan-ga" to "g","gantungan-ha" to "h",
        "gantungan-ja" to "j","gantungan-ka" to "k","gantungan-la" to "l","gantungan-ma" to "m","gantungan-na" to "n",
        "gantungan-nya" to "ny","gantungan-pa" to "p","gantungan-ra" to "r","gantungan-sa" to "s","gantungan-ta" to "t",
        "gantungan-wa" to "w","gantungan-ya" to "y"
    )

    // ===== Aturan vokal hasil kombinasi BASE + tanda vokal =====
    // Param:
    // - mods        : set label tanda yang nempel (ulu/suku/pepet/taleng/tedong)
    // - hasTaleng   : true bila ada 'taleng'
    // - hasTedong   : true bila ada 'tedong'
    // Urutan prioritas:
    //   taleng+tedong → 'o'
    //   taleng        → 'e'
    //   pepet         → 'e'
    //   ulu           → 'i'
    //   suku          → 'u'
    //   (default)     → 'a'
    fun vowelFrom(mods: Set<String>, hasTaleng: Boolean, hasTedong: Boolean): String {
        if (hasTaleng && hasTedong) return "o"
        if (hasTaleng) return "e"
        if ("pepet" in mods) return "e"
        if ("ulu" in mods) return "i"
        if ("suku" in mods) return "u"
        return "a"
    }

    // ===== Sufiks tanda atas (TOP_SIGN) =====
    // - surang → 'r'
    // - cecek  → 'ng'
    // Output dijadikan akhiran suku kata setelah BASE(+vowel)
    fun suffixTop(mods: Set<String>): String {
        val sb = StringBuilder()
        if ("surang" in mods) sb.append("r")
        if ("cecek" in mods)  sb.append("ng")
        return sb.toString()
    }

    // ===== Sufiks tanda akhir (END_SIGN) =====
    // - bisah → 'h'
    // - (adeg-adeg biasanya pemutus / pamaca — implementasi lanjut di logic lain)
    fun suffixEnd(mods: Set<String>): String = if ("bisah" in mods) "h" else ""
}
package com.contoh.soundboxnotifikasi

/**
 * Format CSV untuk kata blokir.
 * - Ditulis dengan UTF-8 + BOM agar Excel membaca huruf dengan benar.
 * - Kata ada di kolom pertama, satu kata per baris, dengan header "kata".
 * - Saat membaca, pemisah koma atau titik koma (Excel Indonesia) sama-sama diterima,
 *   dan kolom lain di sebelah kanan diabaikan.
 */
object KataBlokirCsv {

    private const val BOM = "\uFEFF"
    private const val HEADER = "kata"

    fun buat(kata: Collection<String>): String {
        val sb = StringBuilder()
        sb.append(BOM).append(HEADER).append("\r\n")
        for (k in kata.sorted()) {
            sb.append(escape(k)).append("\r\n")
        }
        return sb.toString()
    }

    fun baca(isi: String): List<String> {
        val teks = isi.removePrefix(BOM).replace("\r\n", "\n").replace('\r', '\n')
        val baris = teks.split('\n')
        val pemisah = if (baris.firstOrNull()?.contains(';') == true) ';' else ','
        val hasil = LinkedHashSet<String>()
        for ((i, b) in baris.withIndex()) {
            if (b.isBlank()) continue
            val kata = pecah(b, pemisah).firstOrNull()?.trim()?.lowercase() ?: continue
            if (i == 0 && kata == HEADER) continue
            if (kata.isNotEmpty()) hasil.add(kata)
        }
        return hasil.toList()
    }

    private fun escape(nilai: String): String {
        val perlu = nilai.any { it == ';' || it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (perlu) "\"" + nilai.replace("\"", "\"\"") + "\"" else nilai
    }

    /** Memecah satu baris CSV dengan dukungan tanda kutip. */
    private fun pecah(baris: String, pemisah: Char): List<String> {
        val kolom = mutableListOf<String>()
        val sb = StringBuilder()
        var dalamKutip = false
        var i = 0
        while (i < baris.length) {
            val c = baris[i]
            when {
                c == '"' && dalamKutip && i + 1 < baris.length && baris[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                c == '"' -> dalamKutip = !dalamKutip
                c == pemisah && !dalamKutip -> {
                    kolom.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        kolom.add(sb.toString())
        return kolom
    }
}

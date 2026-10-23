package com.contoh.soundboxnotifikasi

object IndonesianNumber {

    private val satuan = arrayOf(
        "", "satu", "dua", "tiga", "empat", "lima",
        "enam", "tujuh", "delapan", "sembilan", "sepuluh", "sebelas"
    )

    fun toWords(n: Long): String {
        if (n == 0L) return "nol"
        if (n < 0) return "minus " + toWords(-n)
        return convert(n).trim()
    }

    private fun convert(n: Long): String = when {
        n < 12 -> satuan[n.toInt()]
        n < 20 -> convert(n - 10) + " belas"
        n < 100 -> convert(n / 10) + " puluh" + sisa(n % 10)
        n < 200 -> "seratus" + sisa(n % 100)
        n < 1_000 -> convert(n / 100) + " ratus" + sisa(n % 100)
        n < 2_000 -> "seribu" + sisa(n % 1_000)
        n < 1_000_000 -> convert(n / 1_000) + " ribu" + sisa(n % 1_000)
        n < 1_000_000_000 -> convert(n / 1_000_000) + " juta" + sisa(n % 1_000_000)
        else -> convert(n / 1_000_000_000) + " miliar" + sisa(n % 1_000_000_000)
    }

    private fun sisa(s: Long): String = if (s == 0L) "" else " " + convert(s)
}

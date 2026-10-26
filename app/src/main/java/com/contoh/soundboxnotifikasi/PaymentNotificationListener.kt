package com.contoh.soundboxnotifikasi

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "SoundboxNotif"
        private const val MAKS_ID_DIINGAT = 200
        private const val MAKS_PANJANG_BACAAN = 300
    }

    private var speaker: AmountSpeaker? = null
    private val idTerbaca = LinkedHashSet<String>()

    override fun onListenerConnected() {
        if (speaker == null) speaker = AmountSpeaker(applicationContext)
        Log.d(TAG, "Listener aktif")
    }

    override fun onListenerDisconnected() {
        speaker?.berhenti()
        speaker = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Hanya aplikasi yang dipilih yang dibacakan. Jika belum ada yang dipilih, tidak ada yang dibacakan.
        val dipantau = Pengaturan.paketDipantau(this)
        if (sbn.packageName !in dipantau) return

        val extras = sbn.notification.extras
        val judul = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val isi = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        val teks = "$judul $isi".trim()
        if (teks.isEmpty()) return

        // Notifikasi yang memuat kata blokir tidak dibacakan sama sekali
        val teksKecil = teks.lowercase()
        if (Pengaturan.kataBlokir(this).any { it.isNotEmpty() && teksKecil.contains(it) }) {
            Log.d(TAG, "Diblokir oleh daftar kata: ${sbn.packageName}")
            return
        }

        val namaApp = namaAplikasi(sbn.packageName)
        val sebutNama = Pengaturan.bacakanNamaApp(this)
        val pembayaran = PaymentParser.parse(teks)

        val kunci: String
        val kalimat: String
        if (pembayaran != null) {
            kunci = "${sbn.packageName}|${pembayaran.idTransaksi}"
            val kata = IndonesianNumber.toWords(pembayaran.nominal)
            kalimat = if (sebutNama) {
                "Pembayaran sebesar $kata rupiah diterima, dari $namaApp"
            } else {
                "Pembayaran sebesar $kata rupiah diterima"
            }
        } else if (Pengaturan.bacakanLainnya(this)) {
            kunci = "${sbn.packageName}|$teks"
            val bacaan = if (judul.isNotEmpty() && isi.isNotEmpty()) "$judul. $isi" else teks
            val potong = bacaan.take(MAKS_PANJANG_BACAAN)
            kalimat = if (sebutNama) "Notifikasi dari $namaApp. $potong" else potong
        } else {
            return
        }

        // Notifikasi yang sama sering muncul ulang, jadi cek berdasarkan kunci unik
        if (!idTerbaca.add(kunci)) return
        if (idTerbaca.size > MAKS_ID_DIINGAT) idTerbaca.remove(idTerbaca.first())

        val penyiar = speaker ?: AmountSpeaker(applicationContext).also { speaker = it }
        val berkasKhusus = if (Pengaturan.adaSuaraApp(this, sbn.packageName)) {
            Pengaturan.fileSuaraApp(this, sbn.packageName)
        } else {
            null
        }
        penyiar.ucapkan(kalimat, berkasKhusus)
    }

    /** Nama aplikasi yang mengirim notifikasi, misalnya "ShopeePay". */
    private fun namaAplikasi(paket: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(paket, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        paket
    }
}

data class Pembayaran(val nominal: Long, val idTransaksi: String)

object PaymentParser {
    // Format ShopeePay: "Pembayaran sebesar Rp367 diterima"
    private val polaShopee = Regex(
        """Pembayaran sebesar\s*Rp\s*([\d.]+)\s*diterima""",
        RegexOption.IGNORE_CASE
    )

    // Format umum untuk aplikasi lain: angka Rp dengan kata "diterima" atau "masuk"
    private val polaUmum = Regex("""Rp\s*([\d.]+)""", RegexOption.IGNORE_CASE)
    private val kataMasuk = Regex("""diterima|masuk""", RegexOption.IGNORE_CASE)

    // Contoh isi: "... pada transaksi 100484556171652396."
    private val polaId = Regex("""transaksi\s+(\d+)""", RegexOption.IGNORE_CASE)

    fun parse(teks: String): Pembayaran? {
        val angka = polaShopee.find(teks)?.groupValues?.get(1)
            ?: polaUmum.find(teks)
                ?.takeIf { kataMasuk.containsMatchIn(teks) }
                ?.groupValues?.get(1)
            ?: return null

        val nominal = angka.replace(".", "").toLongOrNull() ?: return null
        if (nominal <= 0) return null

        val id = polaId.find(teks)?.groupValues?.get(1) ?: teks
        return Pembayaran(nominal, id)
    }
}

package com.contoh.soundboxnotifikasi

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class PengaturanNotifikasiActivity : Activity() {

    companion object {
        private const val KODE_BACKUP = 301
        private const val KODE_PULIH = 302
    }

    private lateinit var wadahKata: FlowLayout
    private lateinit var txtJumlah: TextView
    private val daftarKata = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pengaturan_notifikasi)

        val swNama = findViewById<Switch>(R.id.swBacaNama)
        val swLain = findViewById<Switch>(R.id.swBacaLainnya)
        swNama.isChecked = Pengaturan.bacakanNamaApp(this)
        swLain.isChecked = Pengaturan.bacakanLainnya(this)
        swNama.setOnCheckedChangeListener { _, on -> Pengaturan.simpanBacakanNamaApp(this, on) }
        swLain.setOnCheckedChangeListener { _, on -> Pengaturan.simpanBacakanLainnya(this, on) }

        val swVolume = findViewById<Switch>(R.id.swVolumeMaks)
        val skBooster = findViewById<SeekBar>(R.id.skBooster)
        val txtBooster = findViewById<TextView>(R.id.txtBooster)
        swVolume.isChecked = Pengaturan.volumeMaksOtomatis(this)
        swVolume.setOnCheckedChangeListener { _, on -> Pengaturan.simpanVolumeMaks(this, on) }
        skBooster.progress = Pengaturan.boosterPersen(this)
        txtBooster.text = "Booster suara: ${skBooster.progress}%"
        skBooster.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                txtBooster.text = "Booster suara: $progress%"
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}

            override fun onStopTrackingTouch(sb: SeekBar?) {
                Pengaturan.simpanBoosterPersen(this@PengaturanNotifikasiActivity, skBooster.progress)
            }
        })

        txtJumlah = findViewById(R.id.txtJumlahKata)
        wadahKata = findViewById(R.id.wadahKata)

        findViewById<Button>(R.id.btnTambahKata).setOnClickListener { tambahKata() }
        findViewById<Button>(R.id.btnBackupKata).setOnClickListener { bukaBackup() }
        findViewById<Button>(R.id.btnPulihKata).setOnClickListener { bukaPulih() }
        findViewById<Button>(R.id.btnKembaliNotif).setOnClickListener { finish() }

        muatKata()
    }

    /** Semua kata ditampilkan sebagai chip yang berpindah baris. */
    private fun muatKata() {
        daftarKata.clear()
        daftarKata.addAll(Pengaturan.kataBlokir(this).sorted())
        wadahKata.removeAllViews()
        val padH = dp(12)
        val padV = dp(7)
        for (kata in daftarKata) {
            val chip = TextView(this)
            chip.text = "$kata  ×"
            chip.textSize = 14f
            chip.setTextColor(Color.parseColor("#E65100"))
            chip.background = getDrawable(R.drawable.bg_chip)
            chip.setPadding(padH, padV, padH, padV)
            chip.setOnClickListener { konfirmasiHapus(kata) }
            wadahKata.addView(chip)
        }
        txtJumlah.text = if (daftarKata.isEmpty()) {
            "Belum ada kata yang diblokir."
        } else {
            "${daftarKata.size} kata diblokir. Ketuk kata untuk menghapus."
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun tambahKata() {
        val input = findViewById<EditText>(R.id.edtKataBlokir)
        val kata = input.text.toString().trim().lowercase()
        if (kata.isEmpty()) {
            Toast.makeText(this, "Tulis kata yang ingin diblokir dulu", Toast.LENGTH_SHORT).show()
            return
        }
        if (Pengaturan.kataBlokir(this).contains(kata)) {
            Toast.makeText(this, "Kata itu sudah ada di daftar", Toast.LENGTH_SHORT).show()
            return
        }
        Pengaturan.tambahKataBlokir(this, kata)
        input.setText("")
        muatKata()
    }

    private fun konfirmasiHapus(kata: String) {
        AlertDialog.Builder(this)
            .setTitle("Hapus kata?")
            .setMessage("\"$kata\" tidak lagi diblokir. Notifikasi yang memuat kata ini bisa dibacakan lagi.")
            .setPositiveButton("Hapus") { _, _ ->
                Pengaturan.hapusKataBlokir(this, kata)
                muatKata()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ---------- Backup & pulihkan CSV ----------

    private fun bukaBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "kata_blokir_harinfood.csv")
        }
        startActivityForResult(intent, KODE_BACKUP)
    }

    private fun bukaPulih() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain", "application/vnd.ms-excel")
            )
        }
        startActivityForResult(intent, KODE_PULIH)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            KODE_BACKUP -> simpanBackup(uri)
            KODE_PULIH -> konfirmasiPulih(uri)
        }
    }

    private fun simpanBackup(uri: Uri) {
        try {
            val daftar = Pengaturan.kataBlokir(this)
            val isi = KataBlokirCsv.buat(daftar)
            val tulis = contentResolver.openOutputStream(uri)
            if (tulis == null) {
                Toast.makeText(this, "Gagal membuka file tujuan", Toast.LENGTH_SHORT).show()
                return
            }
            tulis.use { it.write(isi.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(this, "Backup tersimpan: ${daftar.size} kata", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Backup gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun konfirmasiPulih(uri: Uri) {
        val isi = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            null
        }
        if (isi == null) {
            Toast.makeText(this, "Gagal membaca file", Toast.LENGTH_SHORT).show()
            return
        }
        val daftarBaru = KataBlokirCsv.baca(isi)
        if (daftarBaru.isEmpty()) {
            Toast.makeText(this, "File tidak berisi kata", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Pulihkan ${daftarBaru.size} kata?")
            .setItems(arrayOf("Gabung dengan daftar sekarang", "Ganti seluruh daftar", "Batal")) { _, pilihan ->
                when (pilihan) {
                    0 -> {
                        Pengaturan.gabungKataBlokir(this, daftarBaru)
                        Toast.makeText(this, "Kata berhasil digabung", Toast.LENGTH_SHORT).show()
                        muatKata()
                    }
                    1 -> {
                        Pengaturan.gantiKataBlokir(this, daftarBaru)
                        Toast.makeText(this, "Daftar diganti dengan ${daftarBaru.size} kata", Toast.LENGTH_SHORT).show()
                        muatKata()
                    }
                }
            }
            .show()
    }
}

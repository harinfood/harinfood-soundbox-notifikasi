package com.contoh.soundboxnotifikasi

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val KODE_PILIH_MP3 = 101
    }

    private lateinit var speaker: AmountSpeaker
    private lateinit var txtSuara: TextView
    private lateinit var txtAkses: TextView
    private lateinit var txtRingkasan: TextView
    private lateinit var barisDropdown: LinearLayout
    private lateinit var panelAplikasi: LinearLayout
    private lateinit var imgPanah: ImageView
    private lateinit var cariAplikasi: EditText
    private lateinit var listAplikasi: ScrollView
    private lateinit var adapter: AplikasiAdapter

    private val dipilih = mutableSetOf<String>()
    private var dropdownBuka = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        speaker = AmountSpeaker(applicationContext)

        txtSuara = findViewById(R.id.txtSuara)
        txtAkses = findViewById(R.id.txtAkses)
        txtRingkasan = findViewById(R.id.txtRingkasan)
        barisDropdown = findViewById(R.id.barisDropdown)
        panelAplikasi = findViewById(R.id.panelAplikasi)
        imgPanah = findViewById(R.id.imgPanah)
        cariAplikasi = findViewById(R.id.cariAplikasi)
        listAplikasi = findViewById(R.id.listAplikasi)

        // Warna tombol: biru, hijau, ungu, oranye
        gayaTombol(findViewById<Button>(R.id.btnIzin), getColor(R.color.biru), getColor(R.color.biru2), R.drawable.ic_lonceng)
        gayaTombol(findViewById<Button>(R.id.btnTes), getColor(R.color.hijau_a), getColor(R.color.hijau_b), R.drawable.ic_play)
        gayaTombol(findViewById<Button>(R.id.btnPilihMp3), getColor(R.color.ungu), getColor(R.color.ungu2), R.drawable.ic_musik)
        gayaTombol(findViewById<Button>(R.id.btnResetMp3), getColor(R.color.oranye2), getColor(R.color.oranye3), R.drawable.ic_hp)
        gayaTombol(findViewById<Button>(R.id.btnPengaturanNotif), getColor(R.color.ungu), getColor(R.color.pink), R.drawable.ic_suara)
        findViewById<Button>(R.id.btnPengaturanNotif).setOnClickListener {
            startActivity(Intent(this, PengaturanNotifikasiActivity::class.java))
        }

        findViewById<Button>(R.id.btnIzin).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.btnTes).setOnClickListener {
            speaker.ucapkan("Pembayaran sebesar dua puluh lima ribu rupiah diterima")
        }

        findViewById<Button>(R.id.btnPilihMp3).setOnClickListener {
            bukaPemilihMp3()
        }

        findViewById<Button>(R.id.btnResetMp3).setOnClickListener {
            Pengaturan.hapusSuara(this)
            perbaruiStatus()
            Toast.makeText(this, "Memakai nada notifikasi HP", Toast.LENGTH_SHORT).show()
        }

        efekTekan(barisDropdown)
        barisDropdown.setOnClickListener { aturDropdown(!dropdownBuka) }

        cariAplikasi.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.saring(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        muatAplikasi()
        perbaruiStatus()
    }

    override fun onResume() {
        super.onResume()
        perbaruiStatus()
    }

    private fun perbaruiStatus() {
        txtSuara.text = if (Pengaturan.adaSuaraKustom(this)) {
            "Suara: ${Pengaturan.namaSuara(this) ?: "file mp3"}"
        } else {
            "Suara: nada notifikasi HP"
        }

        val aktif = aksesNotifikasiAktif()
        txtAkses.text = if (aktif) "Akses notifikasi: aktif" else "Akses notifikasi: belum aktif"
        txtAkses.setTextColor(getColor(if (aktif) R.color.hijau else R.color.merah))
    }

    private fun aksesNotifikasiAktif(): Boolean {
        val daftar = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return daftar?.contains(packageName) == true
    }

    private fun aturDropdown(buka: Boolean) {
        dropdownBuka = buka
        if (buka) {
            panelAplikasi.alpha = 0f
            panelAplikasi.visibility = View.VISIBLE
            panelAplikasi.animate().alpha(1f).setDuration(220).start()
        } else {
            panelAplikasi.visibility = View.GONE
        }
        imgPanah.animate().rotation(if (buka) 180f else 0f).setDuration(220).start()
    }

    private fun muatAplikasi() {
        val pm = packageManager
        val daftar = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != packageName }
            .map { AplikasiItem(pm.getApplicationLabel(it).toString(), it.packageName) }
            .sortedBy { it.label.lowercase() }

        dipilih.addAll(Pengaturan.paketDipantau(this))
        adapter = AplikasiAdapter(this, daftar, dipilih) { simpanPilihan() }
        adapter.attach(findViewById<android.widget.LinearLayout>(R.id.containerAplikasi))
        perbaruiRingkasan()
    }

    private fun simpanPilihan() {
        Pengaturan.simpanPaket(this, dipilih.toSet())
        perbaruiRingkasan()
    }

    private fun perbaruiRingkasan() {
        txtRingkasan.text = if (dipilih.isEmpty()) {
            "Belum ada aplikasi dipilih. Tidak ada notifikasi yang dibacakan."
        } else {
            "${dipilih.size} aplikasi dipilih"
        }
    }

    private fun bukaPemilihMp3() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, KODE_PILIH_MP3)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == KODE_PILIH_MP3 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val berhasil = Pengaturan.salinSuara(this, uri, namaFile(uri))
            Toast.makeText(
                this,
                if (berhasil) "MP3 tersimpan" else "Gagal menyalin MP3",
                Toast.LENGTH_SHORT
            ).show()
            perbaruiStatus()
        }
    }

    private fun namaFile(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return uri.lastPathSegment ?: "file dipilih"
    }
}

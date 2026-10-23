package com.contoh.soundboxnotifikasi

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class PengaturanNotifikasiActivity : Activity() {

    private lateinit var adapter: ArrayAdapter<String>
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

        txtJumlah = findViewById(R.id.txtJumlahKata)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, daftarKata)
        val list = findViewById<ListView>(R.id.listKataBlokir)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, posisi, _ -> konfirmasiHapus(daftarKata[posisi]) }

        findViewById<Button>(R.id.btnTambahKata).setOnClickListener { tambahKata() }
        findViewById<Button>(R.id.btnKembaliNotif).setOnClickListener { finish() }

        muatKata()
    }

    private fun muatKata() {
        daftarKata.clear()
        daftarKata.addAll(Pengaturan.kataBlokir(this).sorted())
        adapter.notifyDataSetChanged()
        txtJumlah.text = if (daftarKata.isEmpty()) {
            "Belum ada kata yang diblokir."
        } else {
            "${daftarKata.size} kata diblokir. Ketuk kata untuk menghapus."
        }
    }

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
}

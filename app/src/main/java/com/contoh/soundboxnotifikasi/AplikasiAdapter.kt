package com.contoh.soundboxnotifikasi

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

data class AplikasiItem(val label: String, val paket: String)

/**
 * Daftar aplikasi untuk ListView yang bisa digulir.
 * Aplikasi yang dipilih selalu di urutan paling atas, sisanya urut nama.
 */
class AplikasiAdapter(
    private val context: Context,
    private val semua: List<AplikasiItem>,
    private val dipilih: MutableSet<String>,
    private val onBerubah: () -> Unit
) : BaseAdapter() {

    private var kataSaring = ""
    private var tampil: List<AplikasiItem> = susun(semua)
    private val pm = context.packageManager

    private fun susun(daftar: List<AplikasiItem>): List<AplikasiItem> =
        daftar.sortedWith(
            compareByDescending<AplikasiItem> { it.paket in dipilih }.thenBy { it.label.lowercase() }
        )

    private fun perbarui() {
        val sumber = if (kataSaring.isEmpty()) {
            semua
        } else {
            semua.filter { it.label.lowercase().contains(kataSaring) }
        }
        tampil = susun(sumber)
        notifyDataSetChanged()
    }

    fun saring(kata: String) {
        kataSaring = kata.trim().lowercase()
        perbarui()
    }

    override fun getCount(): Int = tampil.size

    override fun getItem(position: Int): Any = tampil[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
        buatBaris(tampil[position], parent?.context ?: context)

    private fun buatBaris(item: AplikasiItem, ctx: Context): View {
        val terpilih = item.paket in dipilih

        val baris = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(16), ctx.dp(10), ctx.dp(16), ctx.dp(10))
            val alas = if (terpilih) ColorDrawable(Color.parseColor("#FFF3E0")) else null
            background = RippleDrawable(ColorStateList.valueOf(0x33FB8C00), alas, null)
        }

        val ikon = ImageView(ctx).apply {
            setImageDrawable(runCatching { pm.getApplicationIcon(item.paket) }.getOrNull())
            layoutParams = LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(40))
        }

        val teks = TextView(ctx).apply {
            text = item.label
            textSize = 16f
            setTextColor(Color.parseColor("#263238"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ctx.dp(14)
            }
        }

        val cek = CheckBox(ctx).apply {
            isChecked = terpilih
            isClickable = false
            isFocusable = false
            buttonTintList = ColorStateList.valueOf(Color.parseColor("#FB8C00"))
        }

        baris.addView(ikon)
        baris.addView(teks)
        baris.addView(cek)

        baris.setOnClickListener {
            if (item.paket in dipilih) dipilih.remove(item.paket) else dipilih.add(item.paket)
            onBerubah()
            perbarui() // aplikasi yang baru dipilih langsung naik ke atas
        }
        efekTekan(baris)

        return baris
    }
}

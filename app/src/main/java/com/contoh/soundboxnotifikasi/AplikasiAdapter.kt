package com.contoh.soundboxnotifikasi

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
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

class AplikasiAdapter(
    private val context: Context,
    private val semua: List<AplikasiItem>,
    private val dipilih: MutableSet<String>,
    private val onBerubah: () -> Unit
) : BaseAdapter() {

    private var tampil: List<AplikasiItem> = semua
    private val pm = context.packageManager

    fun saring(kata: String) {
        val kunci = kata.trim().lowercase()
        tampil = if (kunci.isEmpty()) semua else semua.filter { it.label.lowercase().contains(kunci) }
        notifyDataSetChanged()
    }

    override fun getCount(): Int = tampil.size

    override fun getItem(position: Int): Any = tampil[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val ctx = parent?.context ?: context
        val item = tampil[position]

        val baris = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(16), ctx.dp(10), ctx.dp(16), ctx.dp(10))
            background = RippleDrawable(ColorStateList.valueOf(0x33FB8C00), null, null)
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
            isChecked = item.paket in dipilih
            isClickable = false
            isFocusable = false
            buttonTintList = ColorStateList.valueOf(Color.parseColor("#FB8C00"))
        }

        baris.addView(ikon)
        baris.addView(teks)
        baris.addView(cek)

        baris.setOnClickListener {
            val sebelumnya = item.paket in dipilih
            if (sebelumnya) dipilih.remove(item.paket) else dipilih.add(item.paket)
            cek.isChecked = !sebelumnya
            onBerubah()
        }
        efekTekan(baris)

        return baris
    }
}

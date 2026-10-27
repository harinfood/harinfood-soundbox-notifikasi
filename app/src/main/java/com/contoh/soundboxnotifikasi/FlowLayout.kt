package com.contoh.soundboxnotifikasi

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

/** Susunan anak yang berpindah baris bila tidak muat, dan tingginya mengikuti isi. */
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private val jarak = (6 * context.resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val lebarTersedia = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var tinggiBaris = 0
        for (i in 0 until childCount) {
            val anak = getChildAt(i)
            if (anak.visibility == View.GONE) continue
            anak.measure(
                MeasureSpec.makeMeasureSpec(lebarTersedia, MeasureSpec.AT_MOST),
                MeasureSpec.UNSPECIFIED
            )
            val cw = anak.measuredWidth
            val ch = anak.measuredHeight
            if (x > 0 && x + cw > lebarTersedia) {
                x = 0
                y += tinggiBaris + jarak
                tinggiBaris = 0
            }
            x += cw + jarak
            tinggiBaris = max(tinggiBaris, ch)
        }
        val tinggi = y + tinggiBaris + paddingTop + paddingBottom
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), tinggi)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val lebarTersedia = (r - l) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var tinggiBaris = 0
        for (i in 0 until childCount) {
            val anak = getChildAt(i)
            if (anak.visibility == View.GONE) continue
            val cw = anak.measuredWidth
            val ch = anak.measuredHeight
            if (x > 0 && x + cw > lebarTersedia) {
                x = 0
                y += tinggiBaris + jarak
                tinggiBaris = 0
            }
            val kiri = paddingLeft + x
            val atas = paddingTop + y
            anak.layout(kiri, atas, kiri + cw, atas + ch)
            x += cw + jarak
            tinggiBaris = max(tinggiBaris, ch)
        }
    }
}

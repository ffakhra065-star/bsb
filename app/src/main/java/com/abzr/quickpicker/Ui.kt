package com.abzr.quickpicker

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Button

object Ui {
    const val BG = 0xFF0B1120.toInt()
    const val CARD = 0xFF1E293B.toInt()
    const val ACCENT = 0xFF6366F1.toInt()
    const val MUTED = 0xFF334155.toInt()
    const val SUB = 0xFF94A3B8.toInt()

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun round(color: Int, radiusPx: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = radiusPx.toFloat()
    }

    fun btn(c: Context, text: String, primary: Boolean = true) = Button(c).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.WHITE)
        stateListAnimator = null
        setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10))
        background = round(if (primary) ACCENT else MUTED, dp(c, 12))
    }

    fun style(b: Button, on: Boolean) {
        b.background = round(if (on) ACCENT else MUTED, dp(b.context, 12))
    }
}

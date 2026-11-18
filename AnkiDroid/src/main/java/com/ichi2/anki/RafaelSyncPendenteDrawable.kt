// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/**
 * AnkiDroid Rafael: icone de sincronizar com um "?" no meio,
 * usado quando ha mudancas ainda nao sincronizadas.
 */
@Suppress("OVERRIDE_DEPRECATION")
class RafaelSyncPendenteDrawable(
    private val base: Drawable,
) : Drawable() {
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFC107.toInt()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

    override fun draw(canvas: Canvas) {
        base.bounds = bounds
        base.draw(canvas)
        paint.textSize = bounds.height() * 0.46f
        val y = bounds.exactCenterY() - (paint.descent() + paint.ascent()) / 2
        canvas.drawText("?", bounds.exactCenterX(), y, paint)
    }

    override fun setAlpha(alpha: Int) {
        base.alpha = alpha
    }

    // o filtro de cor vale so para as setas; o "?" continua amarelo
    override fun setColorFilter(colorFilter: ColorFilter?) {
        base.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = base.intrinsicWidth

    override fun getIntrinsicHeight(): Int = base.intrinsicHeight
}
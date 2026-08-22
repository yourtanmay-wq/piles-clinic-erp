package com.tkbiswas.pilesclinic.native

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * TK APPROVED (2026-07-15): the Patient card's payment badge used to be a
 * solid, fully-filled circle with the percentage written on top — it always
 * looked "100% full" no matter the real percentage. This draws a real
 * progress ring: a light background track plus a green arc that fills only
 * as much of the circle as the percentage paid, with the number centered —
 * same tap target, same size, only how it's drawn changed.
 */
class PaymentRingView(context: Context) : View(context) {

    var percent: Int = 0
        set(value) { field = value.coerceIn(0, 100); invalidate() }

    private val density = context.resources.displayMetrics.density
    private val strokeWidth = 5f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@PaymentRingView.strokeWidth
        color = Color.parseColor("#E1E6ED")
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@PaymentRingView.strokeWidth
        color = Color.parseColor("#16A36D")
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10223A")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val arcRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = strokeWidth / 2f + 1f
        arcRect.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)
        if (percent > 0) {
            val sweep = 360f * (percent / 100f)
            canvas.drawArc(arcRect, -90f, sweep, false, progressPaint)
        }
        textPaint.textSize = height * 0.32f
        val fm = textPaint.fontMetrics
        val textY = height / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText("$percent%", width / 2f, textY, textPaint)
    }
}

package com.auralis.tv

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Slowly drifting aurora light with a large clock.
 * The clock nudges a few pixels every minute so a QLED panel never shows a static image.
 */
class AuroraView(context: Context) : View(context) {

    private val colors = intArrayOf(
        0xFF2DE2E6.toInt(), // cyan
        0xFF9B5DE5.toInt(), // violet
        0xFFF15BB5.toInt(), // magenta
        0xFF4D7CFF.toInt(), // blue
    )
    private val blob = Paint(Paint.ANTI_ALIAS_FLAG)
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2F5FF.toInt()
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCB9C4EA.toInt()
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
    private val t0 = SystemClock.uptimeMillis()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(0xFF05081A.toInt())

        val t = (SystemClock.uptimeMillis() - t0) / 1000f
        for (i in colors.indices) {
            val cx = w * (0.5f + 0.38f * sin(t * 0.07f * (i + 1) + i * 1.7f))
            val cy = h * (0.5f + 0.32f * cos(t * 0.05f * (i + 2) + i * 2.3f))
            val r = min(w, h) * (0.55f + 0.10f * sin(t * 0.11f + i))
            val rgb = colors[i] and 0x00FFFFFF
            blob.shader = RadialGradient(cx, cy, r, rgb or (0x3C shl 24), rgb, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, blob)
        }

        val now = Date()
        val minute = System.currentTimeMillis() / 60000
        val dx = ((minute * 53) % 81 - 40).toFloat()
        val dy = ((minute * 29) % 61 - 30).toFloat()

        timePaint.textSize = h * 0.26f
        datePaint.textSize = h * 0.045f
        canvas.drawText(timeFmt.format(now), w / 2 + dx, h * 0.52f + dy, timePaint)
        canvas.drawText(dateFmt.format(now), w / 2 + dx, h * 0.52f + h * 0.09f + dy, datePaint)

        postInvalidateDelayed(40)
    }
}

/** Full-screen clock you can start from the Home screen. Any key closes it. */
class AmbientActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(AuroraView(this))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        finish()
        return true
    }
}

/** The same aurora as a system screensaver. */
class AuroraDream : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(AuroraView(this))
    }
}

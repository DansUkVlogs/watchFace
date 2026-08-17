package uk.dansvlogs.clinicalwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class ClinicalWatchView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var ambient = false
    private var battery = 0

    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, if (ambient) 1000L else 50L)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) battery = (level * 100 / scale)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        handler.post(ticker)
    }

    fun setAmbient(value: Boolean) {
        ambient = value
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        try { context.unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) * 0.45f
        val now = System.currentTimeMillis()
        val date = Date(now)
        val calendar = java.util.Calendar.getInstance().apply { time = date }

        canvas.drawColor(Color.BLACK)
        drawTicks(canvas, cx, cy, r)
        drawCardinals(canvas, cx, cy, r)

        if (!ambient) {
            drawDateAndBattery(canvas, cx, cy, r, date)
        }

        val hour = calendar.get(java.util.Calendar.HOUR)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val second = calendar.get(java.util.Calendar.SECOND)
        val millis = calendar.get(java.util.Calendar.MILLISECOND)

        val secondValue = if (ambient) second.toFloat() else second + millis / 1000f
        val minuteValue = minute + secondValue / 60f
        val hourValue = hour + minuteValue / 60f

        drawHand(canvas, cx, cy, r * 0.50f, hourValue * 30f, if (ambient) 5f else 7f, Color.WHITE)
        drawHand(canvas, cx, cy, r * 0.72f, minuteValue * 6f, if (ambient) 4f else 6f, Color.WHITE)
        drawSecondHand(canvas, cx, cy, r, secondValue * 6f)

        paint.style = Paint.Style.FILL
        paint.color = if (ambient) Color.LTGRAY else Color.WHITE
        canvas.drawCircle(cx, cy, if (ambient) 4f else 6f, paint)
    }

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 60) {
            val angle = Math.toRadians((i * 6 - 90).toDouble())
            val major = i % 5 == 0
            val clinical = i % 15 == 0
            val outer = r
            val inner = r - when {
                clinical -> r * 0.12f
                major -> r * 0.085f
                else -> r * 0.04f
            }
            paint.color = when {
                clinical && !ambient -> Color.rgb(255, 70, 70)
                else -> Color.WHITE
            }
            paint.strokeWidth = when {
                clinical -> 5f
                major -> 3.5f
                else -> 1.5f
            }
            canvas.drawLine(
                cx + cos(angle).toFloat() * inner,
                cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer,
                cy + sin(angle).toFloat() * outer,
                paint
            )
        }
    }

    private fun drawCardinals(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = r * 0.115f
        paint.color = Color.WHITE
        val offset = r * 0.71f
        canvas.drawText("60", cx, cy - offset + paint.textSize / 3f, paint)
        canvas.drawText("15", cx + offset, cy + paint.textSize / 3f, paint)
        canvas.drawText("30", cx, cy + offset + paint.textSize / 3f, paint)
        canvas.drawText("45", cx - offset, cy + paint.textSize / 3f, paint)
    }

    private fun drawDateAndBattery(canvas: Canvas, cx: Float, cy: Float, r: Float, date: Date) {
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = r * 0.075f
        paint.color = Color.LTGRAY
        val dateText = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(date).uppercase(Locale.getDefault())
        canvas.drawText(dateText, cx, cy + r * 0.30f, paint)
        canvas.drawText("$battery%", cx, cy + r * 0.41f, paint)
    }

    private fun drawHand(canvas: Canvas, cx: Float, cy: Float, length: Float, degrees: Float, width: Float, color: Int) {
        val angle = Math.toRadians((degrees - 90).toDouble())
        paint.color = color
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(cx, cy, cx + cos(angle).toFloat() * length, cy + sin(angle).toFloat() * length, paint)
    }

    private fun drawSecondHand(canvas: Canvas, cx: Float, cy: Float, r: Float, degrees: Float) {
        val angle = Math.toRadians((degrees - 90).toDouble())
        paint.color = if (ambient) Color.WHITE else Color.rgb(255, 70, 70)
        paint.strokeWidth = if (ambient) 2f else 3f
        paint.strokeCap = Paint.Cap.ROUND
        val tip = r * 0.88f
        val tail = r * 0.14f
        canvas.drawLine(
            cx - cos(angle).toFloat() * tail,
            cy - sin(angle).toFloat() * tail,
            cx + cos(angle).toFloat() * tip,
            cy + sin(angle).toFloat() * tip,
            paint
        )
    }
}

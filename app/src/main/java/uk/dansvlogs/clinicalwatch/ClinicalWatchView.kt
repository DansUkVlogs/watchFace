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
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
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
    private var mode = Mode.CLOCK
    private var stopwatchRunning = false
    private var stopwatchBase = 0L
    private var stopwatchElapsed = 0L

    // Sensor integration is intentionally isolated for the next pass. Until permission is
    // granted these display placeholders rather than inventing health data.
    private var steps: Long? = null
    private var pulse: Int? = null

    private enum class Mode { CLOCK, STOPWATCH }

    private val lime = Color.rgb(185, 235, 0)
    private val panel = Color.rgb(8, 10, 9)
    private val softWhite = Color.rgb(225, 230, 218)

    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            val delay = if (mode == Mode.STOPWATCH && stopwatchRunning && !ambient) 100L else 1000L
            handler.postDelayed(this, delay)
        }
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val cx = width / 2f
            val cy = height / 2f
            val dx = e.x - cx
            val dy = e.y - cy
            val radius = min(width, height) * 0.34f
            if (dx * dx + dy * dy <= radius * radius) {
                mode = if (mode == Mode.CLOCK) Mode.STOPWATCH else Mode.CLOCK
                restartTicker()
                invalidate()
                return true
            }
            return false
        }
    })

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) battery = level * 100 / scale
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        handler.post(ticker)
    }

    fun setAmbient(value: Boolean) {
        ambient = value
        restartTicker()
        invalidate()
    }

    private fun restartTicker() {
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestureDetector.onTouchEvent(event)) return true
        if (event.action == MotionEvent.ACTION_UP && mode == Mode.STOPWATCH && !ambient) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (event.y in h * 0.66f..h * 0.86f) {
                if (event.x < w / 2f) toggleStopwatch() else resetStopwatch()
                return true
            }
        }
        return true
    }

    private fun toggleStopwatch() {
        if (stopwatchRunning) {
            stopwatchElapsed = currentElapsed()
            stopwatchRunning = false
        } else {
            stopwatchBase = SystemClock.elapsedRealtime() - stopwatchElapsed
            stopwatchRunning = true
        }
        restartTicker()
        invalidate()
    }

    private fun resetStopwatch() {
        stopwatchRunning = false
        stopwatchElapsed = 0L
        stopwatchBase = SystemClock.elapsedRealtime()
        restartTicker()
        invalidate()
    }

    private fun currentElapsed(): Long = if (stopwatchRunning) {
        SystemClock.elapsedRealtime() - stopwatchBase
    } else stopwatchElapsed

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        try { context.unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * 0.48f
        drawInstrumentDial(canvas, cx, cy, r)
        if (mode == Mode.CLOCK) drawClock(canvas, cx, cy, r) else drawStopwatch(canvas, cx, cy, r)
    }

    private fun drawInstrumentDial(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        paint.style = Paint.Style.STROKE
        paint.color = if (ambient) Color.DKGRAY else Color.rgb(40, 43, 40)
        paint.strokeWidth = r * 0.018f
        canvas.drawCircle(cx, cy, r * 0.96f, paint)
        canvas.drawCircle(cx, cy, r * 0.86f, paint)
        paint.strokeCap = Paint.Cap.SQUARE
        for (i in 0 until 60) {
            val a = Math.toRadians((i * 6 - 90).toDouble())
            val major = i % 5 == 0
            val clinical = i % 15 == 0
            val outer = r * 0.93f
            val inner = outer - r * if (major) 0.09f else 0.045f
            paint.color = if (clinical && !ambient) lime else softWhite
            paint.strokeWidth = r * if (major) 0.022f else 0.009f
            canvas.drawLine(cx + cos(a).toFloat()*inner, cy + sin(a).toFloat()*inner,
                cx + cos(a).toFloat()*outer, cy + sin(a).toFloat()*outer, paint)
        }
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = r * 0.12f
        paint.color = if (ambient) softWhite else lime
        val rr = r * 0.76f
        canvas.drawText("60", cx, cy-rr+paint.textSize/3, paint)
        canvas.drawText("15", cx+rr, cy+paint.textSize/3, paint)
        canvas.drawText("30", cx, cy+rr+paint.textSize/3, paint)
        canvas.drawText("45", cx-rr, cy+paint.textSize/3, paint)
    }

    private fun drawClock(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val now = Date()
        val cal = java.util.Calendar.getInstance()
        val second = cal.get(java.util.Calendar.SECOND)
        val minute = cal.get(java.util.Calendar.MINUTE)
        val hour = cal.get(java.util.Calendar.HOUR)

        if (!ambient) {
            drawHeader(canvas, cx, cy, r)
            drawInfoPanel(canvas, cx-r*0.56f, cy-r*0.03f, "STEPS", steps?.let { "%,d".format(it) } ?: "--")
            drawInfoPanel(canvas, cx+r*0.56f, cy-r*0.03f, "PULSE", pulse?.toString() ?: "--", "BPM")
            drawDigitalPanel(canvas, cx, cy+r*0.34f, SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now),
                SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(now).uppercase(Locale.getDefault()))
            drawSmallText(canvas, "WATCH  $battery%", cx, cy+r*0.68f, r*0.07f, lime)
        }

        val minuteValue = minute + second / 60f
        val hourValue = hour + minuteValue / 60f
        drawHand(canvas,cx,cy,r*0.47f,hourValue*30f,r*0.055f,softWhite)
        drawHand(canvas,cx,cy,r*0.68f,minuteValue*6f,r*0.04f,softWhite)
        drawSecond(canvas,cx,cy,r,second*6f)
        drawHub(canvas,cx,cy,r)
    }

    private fun drawStopwatch(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val elapsed = currentElapsed()
        val totalTenths = elapsed / 100L
        val tenths = totalTenths % 10
        val totalSeconds = elapsed / 1000L
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        val tickSecond = totalSeconds % 60

        if (!ambient) {
            drawHeader(canvas,cx,cy-r*0.10f,r)
            drawSmallText(canvas,"STOPWATCH",cx,cy+r*0.02f,r*0.075f,Color.LTGRAY)
            drawDigitalPanel(canvas,cx,cy+r*0.20f,String.format(Locale.getDefault(),"%02d:%02d:%02d.%d",hours,minutes,seconds,tenths),"")
            drawButton(canvas,cx-r*0.30f,cy+r*0.55f,r*0.27f,if(stopwatchRunning) "PAUSE" else "START")
            drawButton(canvas,cx+r*0.30f,cy+r*0.55f,r*0.27f,"RESET")
        } else {
            drawSmallText(canvas,String.format(Locale.getDefault(),"%02d:%02d:%02d",hours,minutes,seconds),cx,cy+r*0.25f,r*0.12f,softWhite)
        }
        drawSecond(canvas,cx,cy,r,tickSecond*6f)
        drawHub(canvas,cx,cy,r)
    }

    private fun drawHeader(canvas: Canvas,cx:Float,cy:Float,r:Float) {
        drawSmallText(canvas,"SOUTH CENTRAL AMBULANCE",cx,cy-r*0.45f,r*0.055f,Color.LTGRAY)
        drawSmallText(canvas,"PARAMEDIC",cx,cy-r*0.34f,r*0.085f,softWhite)
        drawSmallText(canvas,"DAN",cx,cy-r*0.23f,r*0.12f,lime)
    }

    private fun drawInfoPanel(canvas:Canvas,x:Float,y:Float,title:String,value:String,suffix:String="") {
        val r=min(width,height)*0.48f
        paint.style=Paint.Style.STROKE; paint.color=Color.rgb(55,60,55); paint.strokeWidth=r*0.012f
        canvas.drawRoundRect(x-r*0.23f,y-r*0.20f,x+r*0.23f,y+r*0.20f,r*0.05f,r*0.05f,paint)
        drawSmallText(canvas,title,x,y-r*0.07f,r*0.065f,Color.LTGRAY)
        drawSmallText(canvas,value,x,y+r*0.06f,r*0.12f,lime)
        if(suffix.isNotEmpty()) drawSmallText(canvas,suffix,x,y+r*0.15f,r*0.055f,Color.LTGRAY)
    }

    private fun drawDigitalPanel(canvas:Canvas,cx:Float,cy:Float,value:String,subtitle:String) {
        val r=min(width,height)*0.48f
        paint.style=Paint.Style.FILL; paint.color=panel
        canvas.drawRoundRect(cx-r*0.52f,cy-r*0.18f,cx+r*0.52f,cy+r*0.18f,r*0.05f,r*0.05f,paint)
        paint.style=Paint.Style.STROKE; paint.color=Color.rgb(80,100,20); paint.strokeWidth=r*0.008f
        canvas.drawRoundRect(cx-r*0.52f,cy-r*0.18f,cx+r*0.52f,cy+r*0.18f,r*0.05f,r*0.05f,paint)
        drawSmallText(canvas,value,cx,cy+r*0.03f,r*0.16f,softWhite)
        if(subtitle.isNotEmpty()) drawSmallText(canvas,subtitle,cx,cy+r*0.13f,r*0.055f,Color.LTGRAY)
    }

    private fun drawButton(canvas:Canvas,cx:Float,cy:Float,halfWidth:Float,label:String) {
        val r=min(width,height)*0.48f
        paint.style=Paint.Style.FILL; paint.color=Color.rgb(20,23,20)
        canvas.drawRoundRect(cx-halfWidth,cy-r*0.12f,cx+halfWidth,cy+r*0.12f,r*0.04f,r*0.04f,paint)
        paint.style=Paint.Style.STROKE; paint.color=Color.rgb(70,75,70); paint.strokeWidth=r*0.01f
        canvas.drawRoundRect(cx-halfWidth,cy-r*0.12f,cx+halfWidth,cy+r*0.12f,r*0.04f,r*0.04f,paint)
        drawSmallText(canvas,label,cx,cy+r*0.025f,r*0.075f,lime)
    }

    private fun drawSmallText(canvas:Canvas,text:String,x:Float,y:Float,size:Float,color:Int) {
        paint.style=Paint.Style.FILL; paint.typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD)
        paint.textAlign=Paint.Align.CENTER; paint.textSize=size; paint.color=color
        canvas.drawText(text,x,y,paint)
    }

    private fun drawHand(canvas:Canvas,cx:Float,cy:Float,length:Float,degrees:Float,width:Float,color:Int) {
        val a=Math.toRadians((degrees-90).toDouble()); paint.style=Paint.Style.STROKE; paint.color=color
        paint.strokeWidth=width; paint.strokeCap=Paint.Cap.ROUND
        canvas.drawLine(cx,cy,cx+cos(a).toFloat()*length,cy+sin(a).toFloat()*length,paint)
    }

    private fun drawSecond(canvas:Canvas,cx:Float,cy:Float,r:Float,degrees:Float) {
        val a=Math.toRadians((degrees-90).toDouble()); paint.style=Paint.Style.STROKE
        paint.color=if(ambient) softWhite else lime; paint.strokeWidth=r*0.018f; paint.strokeCap=Paint.Cap.ROUND
        val tip=r*0.88f; val tail=r*0.12f
        canvas.drawLine(cx-cos(a).toFloat()*tail,cy-sin(a).toFloat()*tail,cx+cos(a).toFloat()*tip,cy+sin(a).toFloat()*tip,paint)
    }

    private fun drawHub(canvas:Canvas,cx:Float,cy:Float,r:Float) {
        paint.style=Paint.Style.FILL; paint.color=Color.BLACK; canvas.drawCircle(cx,cy,r*0.045f,paint)
        paint.style=Paint.Style.STROKE; paint.color=if(ambient) softWhite else lime; paint.strokeWidth=r*0.018f
        canvas.drawCircle(cx,cy,r*0.045f,paint)
    }
}

package uk.dansvlogs.clinicalwatch

import android.content.*
import android.graphics.*
import android.os.*
import android.util.Base64
import android.view.View
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class ClinicalWatchView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("companion", Context.MODE_PRIVATE)
    private var ambient = false
    private var watchBattery = 0
    private var phoneBattery: Int? = null
    private var name = "DAN"
    private var accent = Color.rgb(185, 225, 0)
    private val ivory = Color.rgb(244, 246, 235)
    private var crestBitmap: Bitmap? = null

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { load(); invalidate() }
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) watchBattery = level * 100 / scale
            invalidate()
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            val delay = if (ambient) 60000L else 1000L
            handler.postDelayed(this, delay)
        }
    }

    init {
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ContextCompat.registerReceiver(context, dataReceiver, IntentFilter("uk.dansvlogs.clinicalwatch.DATA_CHANGED"), ContextCompat.RECEIVER_NOT_EXPORTED)
        load()
        handler.post(tick)
    }

    private fun load() {
        name = prefs.getString("name", "DAN") ?: "DAN"
        try { accent = Color.parseColor(prefs.getString("accent", "#B9E100")) } catch (_: Throwable) {}
        phoneBattery = prefs.getString("phoneBattery", null)?.toIntOrNull() ?: phoneBattery
        crestBitmap = try {
            prefs.getString("crest", null)?.let {
                val bytes = Base64.decode(it, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Throwable) { null }
    }

    fun setAmbient(value: Boolean) { ambient = value; restart() }
    fun setExternalSteps(value: Long) { /* retained for MainActivity compatibility; fob face intentionally hides steps */ }
    fun startSensors() { /* pulse intentionally removed from fob design */ }
    fun stopSensors() { }
    private fun restart() { handler.removeCallbacks(tick); handler.post(tick) }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        try { context.unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        try { context.unregisterReceiver(dataReceiver) } catch (_: Exception) {}
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * .495f
        drawBackground(canvas, cx, cy, r)
        drawDial(canvas, cx, cy, r)
        if (!ambient) drawInformation(canvas, cx, cy, r)
        drawHands(canvas, cx, cy, r)
    }

    private fun drawBackground(c: Canvas, cx: Float, cy: Float, r: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, r, intArrayOf(Color.rgb(30,32,32), Color.rgb(12,13,14), Color.BLACK), floatArrayOf(0f,.72f,1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * .018f
        paint.color = Color.rgb(72,76,76)
        c.drawCircle(cx, cy, r * .955f, paint)
        paint.strokeWidth = r * .004f
        paint.color = Color.rgb(110,114,112)
        c.drawCircle(cx, cy, r * .895f, paint)
    }

    private fun drawDial(c: Canvas, cx: Float, cy: Float, r: Float) {
        for (i in 0 until 60) {
            val a = Math.toRadians((i * 6 - 90).toDouble())
            val major = i % 5 == 0
            val cardinal = i % 15 == 0
            val outer = r * .875f
            val inner = outer - r * if (major) .085f else .035f
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = r * if (major) .017f else .006f
            paint.color = if (cardinal && !ambient) accent else ivory
            c.drawLine(cx + cos(a).toFloat()*inner, cy + sin(a).toFloat()*inner, cx + cos(a).toFloat()*outer, cy + sin(a).toFloat()*outer, paint)
        }
    }

    private fun drawInformation(c: Canvas, cx: Float, cy: Float, r: Float) {
        // Custom name: the only branding text, deliberately isolated above the hands.
        text(c, name.uppercase(Locale.getDefault()), cx, cy-r*.555f, r*.092f, ivory, true)

        // Large central digital time. A dark glass capsule protects legibility from the analogue hands.
        val top = cy + r*.185f
        val bottom = cy + r*.485f
        glassPanel(c, cx-r*.56f, top, cx+r*.56f, bottom, r*.055f)
        val now = Date()
        text(c, SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now), cx, cy+r*.365f, r*.155f, ivory, true)

        // Exact requested date format: TUE 18 AUG(08) 2026
        val day = SimpleDateFormat("EEE", Locale.getDefault()).format(now).uppercase(Locale.getDefault())
        val dd = SimpleDateFormat("dd", Locale.getDefault()).format(now)
        val mon = SimpleDateFormat("MMM", Locale.getDefault()).format(now).uppercase(Locale.getDefault())
        val mm = SimpleDateFormat("MM", Locale.getDefault()).format(now)
        val yyyy = SimpleDateFormat("yyyy", Locale.getDefault()).format(now)
        text(c, "$day $dd $mon($mm) $yyyy", cx, cy+r*.585f, r*.066f, ivory, true)

        // Icon-only batteries. Left is a watch-shaped battery, right is a phone-shaped battery.
        drawWatchBattery(c, cx-r*.37f, cy+r*.705f, r*.105f, watchBattery)
        drawPhoneBattery(c, cx+r*.37f, cy+r*.705f, r*.105f, phoneBattery)
    }

    private fun glassPanel(c: Canvas, l: Float, t: Float, rr: Float, b: Float, radius: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(225, 5, 7, 8)
        c.drawRoundRect(l,t,rr,b,radius,radius,paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius*.08f
        paint.color = Color.rgb(72,76,74)
        c.drawRoundRect(l,t,rr,b,radius,radius,paint)
        paint.strokeWidth = radius*.045f
        paint.color = accent
        c.drawRoundRect(l+radius*.12f,t+radius*.12f,rr-radius*.12f,b-radius*.12f,radius*.8f,radius*.8f,paint)
    }

    private fun drawWatchBattery(c: Canvas, x: Float, y: Float, s: Float, value: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s*.12f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = ivory
        c.drawRoundRect(x-s*.62f,y-s*.42f,x+s*.62f,y+s*.42f,s*.20f,s*.20f,paint)
        c.drawLine(x-s*.30f,y-s*.68f,x+s*.30f,y-s*.68f,paint)
        c.drawLine(x-s*.30f,y+s*.68f,x+s*.30f,y+s*.68f,paint)
        c.drawLine(x-s*.30f,y-s*.68f,x-s*.30f,y-s*.43f,paint)
        c.drawLine(x+s*.30f,y-s*.68f,x+s*.30f,y-s*.43f,paint)
        c.drawLine(x-s*.30f,y+s*.43f,x-s*.30f,y+s*.68f,paint)
        c.drawLine(x+s*.30f,y+s*.43f,x+s*.30f,y+s*.68f,paint)
        fillBattery(c,x,y,s*.50f,s*.30f,value)
    }

    private fun drawPhoneBattery(c: Canvas, x: Float, y: Float, s: Float, value: Int?) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s*.12f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = ivory
        c.drawRoundRect(x-s*.48f,y-s*.70f,x+s*.48f,y+s*.70f,s*.18f,s*.18f,paint)
        c.drawCircle(x,y+s*.52f,s*.055f,paint)
        fillBattery(c,x,y-s*.08f,s*.32f,s*.38f,value ?: 0)
    }

    private fun fillBattery(c: Canvas, x: Float, y: Float, halfW: Float, halfH: Float, value: Int) {
        val pct = value.coerceIn(0,100)/100f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(42,46,44)
        c.drawRoundRect(x-halfW,y-halfH,x+halfW,y+halfH,halfH*.18f,halfH*.18f,paint)
        if (pct > 0f) {
            paint.color = accent
            c.drawRoundRect(x-halfW,y-halfH,x-halfW+2f*halfW*pct,y+halfH,halfH*.18f,halfH*.18f,paint)
        }
    }

    private fun drawHands(c: Canvas, cx: Float, cy: Float, r: Float) {
        val cal = Calendar.getInstance()
        val sec = cal.get(Calendar.SECOND)
        val min = cal.get(Calendar.MINUTE)
        val hour = cal.get(Calendar.HOUR)
        val minuteAngle = (min + sec/60f) * 6f
        val hourAngle = (hour + min/60f) * 30f
        hand(c,cx,cy,r*.44f,hourAngle,r*.072f)
        hand(c,cx,cy,r*.65f,minuteAngle,r*.052f)
        if (!ambient) secondHand(c,cx,cy,r,sec*6f)
        hub(c,cx,cy,r)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, len: Float, degrees: Float, width: Float) {
        val a = Math.toRadians((degrees-90).toDouble())
        val ex = cx + cos(a).toFloat()*len
        val ey = cy + sin(a).toFloat()*len
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.BLACK
        paint.strokeWidth = width*1.48f
        c.drawLine(cx,cy,ex,ey,paint)
        paint.color = Color.rgb(105,110,108)
        paint.strokeWidth = width*1.16f
        c.drawLine(cx,cy,ex,ey,paint)
        paint.color = ivory
        paint.strokeWidth = width*.72f
        c.drawLine(cx,cy,ex,ey,paint)
    }

    private fun secondHand(c: Canvas, cx: Float, cy: Float, r: Float, degrees: Float) {
        val a = Math.toRadians((degrees-90).toDouble())
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = r*.012f
        paint.color = accent
        c.drawLine(cx-cos(a).toFloat()*r*.12f,cy-sin(a).toFloat()*r*.12f,cx+cos(a).toFloat()*r*.76f,cy+sin(a).toFloat()*r*.76f,paint)
    }

    private fun hub(c: Canvas, cx: Float, cy: Float, r: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(18,20,20)
        c.drawCircle(cx,cy,r*.060f,paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r*.017f
        paint.color = accent
        c.drawCircle(cx,cy,r*.039f,paint)
        paint.style = Paint.Style.FILL
        paint.color = ivory
        c.drawCircle(cx,cy,r*.012f,paint)
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create("sans-serif-condensed", if (bold) Typeface.BOLD else Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size
        paint.color = color
        c.drawText(value,x,y,paint)
    }
}

package uk.dansvlogs.clinicalwatch

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.hardware.*
import android.os.*
import android.view.*
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class ClinicalWatchView(context: Context) : View(context), SensorEventListener {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val heart = sensors.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private var ambient = false
    private var watchBattery = 0
    private var steps: Long? = null
    private var pulse: Int? = null
    private var mode = 0
    private var running = false
    private var stopwatchBase = 0L
    private var stopwatchElapsed = 0L
    private val accent = Color.rgb(190, 235, 0)
    private val text = Color.rgb(238, 241, 229)
    private val crest: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.crest) }

    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, if (mode == 1 && running && !ambient) 100 else 1000)
        }
    }
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val dx = e.x - width / 2f; val dy = e.y - height / 2f
            if (dx * dx + dy * dy < (min(width, height) * .34f).pow(2)) { mode = 1 - mode; restart(); return true }
            return false
        }
    })
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) watchBattery = level * 100 / scale
        }
    }

    init { context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)); handler.post(ticker) }
    fun setAmbient(value: Boolean) { ambient = value; restart() }
    fun setExternalSteps(value: Long) { steps = value.coerceAtLeast(0); invalidate() }

    fun startSensors() {
        val body = ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        val health = if (Build.VERSION.SDK_INT >= 36) ContextCompat.checkSelfPermission(context, "android.permission.health.READ_HEART_RATE") == PackageManager.PERMISSION_GRANTED else true
        if (body || health) heart?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }
    fun stopSensors() {}
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
            val value = event.values[0].roundToInt()
            if (value in 25..240) pulse = value
            invalidate()
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun restart() { handler.removeCallbacks(ticker); handler.post(ticker) }
    private fun elapsed() = if (running) SystemClock.elapsedRealtime() - stopwatchBase else stopwatchElapsed
    private fun toggleStopwatch() { if (running) { stopwatchElapsed = elapsed(); running = false } else { stopwatchBase = SystemClock.elapsedRealtime() - stopwatchElapsed; running = true }; restart() }
    private fun resetStopwatch() { running = false; stopwatchElapsed = 0; stopwatchBase = SystemClock.elapsedRealtime(); restart() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (gestures.onTouchEvent(e)) return true
        if (e.action == MotionEvent.ACTION_UP && mode == 1 && !ambient && e.y > height * .66f && e.y < height * .89f) { if (e.x < width / 2) toggleStopwatch() else resetStopwatch(); return true }
        return true
    }
    override fun onDetachedFromWindow() { sensors.unregisterListener(this); handler.removeCallbacks(ticker); try { context.unregisterReceiver(batteryReceiver) } catch (_: Exception) {}; super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK); val cx = width / 2f; val cy = height / 2f; val r = min(width, height) * .495f
        drawFace(canvas, cx, cy, r)
        if (mode == 0) drawClock(canvas, cx, cy, r) else drawStopwatch(canvas, cx, cy, r)
    }

    private fun drawFace(c: Canvas, cx: Float, cy: Float, r: Float) {
        paint.style = Paint.Style.FILL; paint.shader = RadialGradient(cx, cy, r, intArrayOf(Color.rgb(22,25,23), Color.rgb(6,8,7), Color.BLACK), floatArrayOf(0f,.72f,1f), Shader.TileMode.CLAMP); c.drawCircle(cx,cy,r*.97f,paint); paint.shader=null
        paint.style=Paint.Style.STROKE; paint.color=Color.rgb(73,78,74); paint.strokeWidth=r*.018f; c.drawCircle(cx,cy,r*.925f,paint)
        for(i in 0 until 60){ val a=Math.toRadians((i*6-90).toDouble()); val major=i%5==0; val outer=r*.91f; val inner=outer-r*(if(major).075f else .03f); paint.color=if(i%15==0&&!ambient)accent else if(major)text else Color.rgb(130,135,131); paint.strokeWidth=r*(if(major).022f else .006f); c.drawLine(cx+cos(a).toFloat()*inner,cy+sin(a).toFloat()*inner,cx+cos(a).toFloat()*outer,cy+sin(a).toFloat()*outer,paint) }
    }

    private fun drawCrest(c: Canvas, cx: Float, top: Float, r: Float) {
        val d=crest ?: return; val hh=(r*.30f).toInt(); val ww=(hh*.80f).toInt(); d.setBounds((cx-ww/2).toInt(),top.toInt(),(cx+ww/2).toInt(),top.toInt()+hh); d.draw(c)
    }

    private fun drawClock(c: Canvas,cx:Float,cy:Float,r:Float){
        val cal=Calendar.getInstance(); val sec=cal.get(Calendar.SECOND); val min=cal.get(Calendar.MINUTE); val hour=cal.get(Calendar.HOUR)
        if(!ambient){
            drawCrest(c,cx,cy-r*.76f,r); label(c,"PARAMEDIC • DAN",cx,cy-r*.39f,r*.055f,text)
            infoCard(c,cx-r*.46f,cy-r*.03f,"STEPS",steps?.let{"%,d".format(it)}?:"--")
            infoCard(c,cx+r*.46f,cy-r*.03f,"PULSE",pulse?.let{"$it BPM"}?:"--")
            digital(c,cx,cy+r*.29f,SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(Date()),SimpleDateFormat("EEE  d MMM",Locale.getDefault()).format(Date()).uppercase(Locale.getDefault()))
            label(c,"WATCH  $watchBattery%",cx,cy+r*.70f,r*.045f,accent)
        }
        val minute=min+sec/60f; hand(c,cx,cy,r*.42f,(hour+minute/60)*30,r*.074f); hand(c,cx,cy,r*.63f,minute*6,r*.05f); second(c,cx,cy,r,sec*6f); hub(c,cx,cy,r)
    }

    private fun drawStopwatch(c:Canvas,cx:Float,cy:Float,r:Float){
        val ms=elapsed(); val total=ms/1000; val tenth=(ms/100)%10; val ss=total%60; val mm=(total/60)%60; val hh=total/3600
        if(!ambient){ drawCrest(c,cx,cy-r*.76f,r); label(c,"STOPWATCH",cx,cy-r*.39f,r*.06f,text); digital(c,cx,cy+r*.15f,String.format(Locale.getDefault(),"%02d:%02d:%02d.%d",hh,mm,ss,tenth),"HOUR     MIN     SEC     1/10"); button(c,cx-r*.29f,cy+r*.53f,r*.25f,if(running)"PAUSE" else "START"); button(c,cx+r*.29f,cy+r*.53f,r*.25f,"RESET") }
        second(c,cx,cy,r,(total%60)*6f); hub(c,cx,cy,r)
    }

    private fun infoCard(c:Canvas,x:Float,y:Float,title:String,value:String){ val r=min(width,height)*.495f; paint.style=Paint.Style.FILL;paint.color=Color.rgb(8,11,9);c.drawRoundRect(x-r*.25f,y-r*.15f,x+r*.25f,y+r*.15f,r*.035f,r*.035f,paint);paint.style=Paint.Style.STROKE;paint.color=Color.rgb(82,88,83);paint.strokeWidth=r*.007f;c.drawRoundRect(x-r*.25f,y-r*.15f,x+r*.25f,y+r*.15f,r*.035f,r*.035f,paint);label(c,title,x,y-r*.04f,r*.045f,Color.LTGRAY);label(c,value,x,y+r*.075f,r*.078f,accent) }
    private fun digital(c:Canvas,x:Float,y:Float,value:String,sub:String){ val r=min(width,height)*.495f;paint.style=Paint.Style.FILL;paint.color=Color.rgb(2,5,3);c.drawRoundRect(x-r*.51f,y-r*.17f,x+r*.51f,y+r*.20f,r*.035f,r*.035f,paint);paint.style=Paint.Style.STROKE;paint.color=Color.rgb(110,137,5);paint.strokeWidth=r*.007f;c.drawRoundRect(x-r*.51f,y-r*.17f,x+r*.51f,y+r*.20f,r*.035f,r*.035f,paint);label(c,value,x,y+r*.025f,r*.14f,text);label(c,sub,x,y+r*.15f,r*.04f,Color.LTGRAY) }
    private fun button(c:Canvas,x:Float,y:Float,half:Float,value:String){ val r=min(width,height)*.495f;paint.style=Paint.Style.FILL;paint.color=Color.rgb(12,15,13);c.drawRoundRect(x-half,y-r*.12f,x+half,y+r*.12f,r*.035f,r*.035f,paint);paint.style=Paint.Style.STROKE;paint.color=Color.rgb(82,88,83);paint.strokeWidth=r*.007f;c.drawRoundRect(x-half,y-r*.12f,x+half,y+r*.12f,r*.035f,r*.035f,paint);label(c,value,x,y+r*.022f,r*.06f,accent) }
    private fun label(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int){paint.shader=null;paint.style=Paint.Style.FILL;paint.typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD);paint.textAlign=Paint.Align.CENTER;paint.textSize=size;paint.color=color;c.drawText(s,x,y,paint)}
    private fun hand(c:Canvas,cx:Float,cy:Float,len:Float,d:Float,w:Float){val a=Math.toRadians((d-90).toDouble());val ex=cx+cos(a).toFloat()*len;val ey=cy+sin(a).toFloat()*len;paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.SQUARE;paint.strokeWidth=w*1.25f;paint.color=Color.BLACK;c.drawLine(cx,cy,ex,ey,paint);paint.strokeWidth=w;paint.color=Color.DKGRAY;c.drawLine(cx,cy,ex,ey,paint);paint.strokeWidth=w*.55f;paint.color=text;c.drawLine(cx,cy,ex,ey,paint)}
    private fun second(c:Canvas,cx:Float,cy:Float,r:Float,d:Float){val a=Math.toRadians((d-90).toDouble());paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.color=if(ambient)text else accent;paint.strokeWidth=r*.014f;c.drawLine(cx-cos(a).toFloat()*r*.12f,cy-sin(a).toFloat()*r*.12f,cx+cos(a).toFloat()*r*.85f,cy+sin(a).toFloat()*r*.85f,paint)}
    private fun hub(c:Canvas,cx:Float,cy:Float,r:Float){paint.style=Paint.Style.FILL;paint.color=Color.BLACK;c.drawCircle(cx,cy,r*.052f,paint);paint.style=Paint.Style.STROKE;paint.color=if(ambient)text else accent;paint.strokeWidth=r*.014f;c.drawCircle(cx,cy,r*.042f,paint)}
}

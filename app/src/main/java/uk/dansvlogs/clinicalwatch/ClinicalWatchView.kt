package uk.dansvlogs.clinicalwatch

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class ClinicalWatchView(context: Context) : View(context), SensorEventListener {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val heartSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private var ambient=false; private var battery=0; private var mode=Mode.CLOCK
    private var stopwatchRunning=false; private var stopwatchBase=0L; private var stopwatchElapsed=0L
    private var steps:Long?=null; private var pulse:Int?=null; private var stepBootBaseline:Long?=null
    private enum class Mode { CLOCK, STOPWATCH }
    private val lime=Color.rgb(185,235,0); private val panel=Color.rgb(8,10,9); private val softWhite=Color.rgb(225,230,218)

    private val ticker=object:Runnable{override fun run(){invalidate();handler.postDelayed(this,if(mode==Mode.STOPWATCH&&stopwatchRunning&&!ambient)100L else 1000L)}}
    private val gestureDetector=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){override fun onDown(e:MotionEvent)=true;override fun onDoubleTap(e:MotionEvent):Boolean{val dx=e.x-width/2f;val dy=e.y-height/2f;val rr=min(width,height)*0.34f;if(dx*dx+dy*dy<=rr*rr){mode=if(mode==Mode.CLOCK)Mode.STOPWATCH else Mode.CLOCK;restartTicker();return true};return false}})
    private val batteryReceiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){val l=i?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1;val s=i?.getIntExtra(BatteryManager.EXTRA_SCALE,-1)?:-1;if(l>=0&&s>0)battery=l*100/s}}

    init{setBackgroundColor(Color.BLACK);context.registerReceiver(batteryReceiver,IntentFilter(Intent.ACTION_BATTERY_CHANGED));handler.post(ticker)}
    fun setAmbient(v:Boolean){ambient=v;restartTicker()}
    fun startSensors(){
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED) stepSensor?.let{sensorManager.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL)}
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.BODY_SENSORS)==PackageManager.PERMISSION_GRANTED) heartSensor?.let{sensorManager.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL)}
    }
    fun stopSensors(){sensorManager.unregisterListener(this)}
    override fun onSensorChanged(e:SensorEvent){when(e.sensor.type){Sensor.TYPE_STEP_COUNTER->{val raw=e.values[0].toLong();if(stepBootBaseline==null)stepBootBaseline=raw;steps=raw-(stepBootBaseline?:raw);invalidate()};Sensor.TYPE_HEART_RATE->{val bpm=e.values[0].toInt();if(bpm>0)pulse=bpm;invalidate()}}}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
    private fun restartTicker(){handler.removeCallbacks(ticker);handler.post(ticker)}
    override fun onTouchEvent(e:MotionEvent):Boolean{if(gestureDetector.onTouchEvent(e))return true;if(e.action==MotionEvent.ACTION_UP&&mode==Mode.STOPWATCH&&!ambient&&e.y in height*.66f..height*.86f){if(e.x<width/2f)toggleStopwatch()else resetStopwatch();return true};return true}
    private fun toggleStopwatch(){if(stopwatchRunning){stopwatchElapsed=currentElapsed();stopwatchRunning=false}else{stopwatchBase=SystemClock.elapsedRealtime()-stopwatchElapsed;stopwatchRunning=true};restartTicker()}
    private fun resetStopwatch(){stopwatchRunning=false;stopwatchElapsed=0;stopwatchBase=SystemClock.elapsedRealtime();restartTicker()}
    private fun currentElapsed()=if(stopwatchRunning)SystemClock.elapsedRealtime()-stopwatchBase else stopwatchElapsed
    override fun onDetachedFromWindow(){stopSensors();handler.removeCallbacks(ticker);try{context.unregisterReceiver(batteryReceiver)}catch(_:Exception){};super.onDetachedFromWindow()}

    override fun onDraw(c:Canvas){super.onDraw(c);c.drawColor(Color.BLACK);val cx=width/2f;val cy=height/2f;val r=min(width,height)*.48f;drawDial(c,cx,cy,r);if(mode==Mode.CLOCK)drawClock(c,cx,cy,r)else drawStopwatch(c,cx,cy,r)}
    private fun drawDial(c:Canvas,cx:Float,cy:Float,r:Float){paint.style=Paint.Style.STROKE;paint.color=Color.rgb(32,35,32);paint.strokeWidth=r*.025f;c.drawCircle(cx,cy,r*.96f,paint);c.drawCircle(cx,cy,r*.84f,paint);for(i in 0 until 60){val a=Math.toRadians((i*6-90).toDouble());val major=i%5==0;val outer=r*.93f;val inner=outer-r*(if(major).085f else .035f);paint.color=if(i%15==0&&!ambient)lime else softWhite;paint.strokeWidth=r*(if(major).025f else .008f);c.drawLine(cx+cos(a).toFloat()*inner,cy+sin(a).toFloat()*inner,cx+cos(a).toFloat()*outer,cy+sin(a).toFloat()*outer,paint)};val rr=r*.76f;text(c,"60",cx,cy-rr+r*.04f,r*.11f,lime);text(c,"15",cx+rr,cy+r*.04f,r*.11f,lime);text(c,"30",cx,cy+rr+r*.04f,r*.11f,lime);text(c,"45",cx-rr,cy+r*.04f,r*.11f,lime)}
    private fun drawClock(c:Canvas,cx:Float,cy:Float,r:Float){val now=Date();val cal=java.util.Calendar.getInstance();val s=cal.get(java.util.Calendar.SECOND);val m=cal.get(java.util.Calendar.MINUTE);val h=cal.get(java.util.Calendar.HOUR);if(!ambient){text(c,"SCAS",cx,cy-r*.52f,r*.11f,lime);text(c,"PARAMEDIC",cx,cy-r*.37f,r*.075f,softWhite);text(c,"DAN",cx,cy-r*.26f,r*.105f,lime);info(c,cx-r*.55f,cy-r*.02f,"STEPS",steps?.let{"%,d".format(it)}?:"--","");info(c,cx+r*.55f,cy-r*.02f,"PULSE",pulse?.toString()?:"--","BPM");digital(c,cx,cy+r*.34f,SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(now),SimpleDateFormat("EEE d MMM yyyy",Locale.getDefault()).format(now).uppercase());text(c,"WATCH  $battery%",cx,cy+r*.68f,r*.065f,lime)};val mv=m+s/60f;drawHand(c,cx,cy,r*.47f,(h+mv/60)*30,r*.055f);drawHand(c,cx,cy,r*.68f,mv*6,r*.04f);second(c,cx,cy,r,s*6f);hub(c,cx,cy,r)}
    private fun drawStopwatch(c:Canvas,cx:Float,cy:Float,r:Float){val e=currentElapsed();val sec=e/1000;val t=(e/100)%10;val s=sec%60;val m=(sec/60)%60;val h=sec/3600;if(!ambient){text(c,"SCAS",cx,cy-r*.52f,r*.11f,lime);text(c,"PARAMEDIC",cx,cy-r*.37f,r*.075f,softWhite);text(c,"DAN",cx,cy-r*.26f,r*.105f,lime);text(c,"STOPWATCH",cx,cy+r*.02f,r*.065f,Color.LTGRAY);digital(c,cx,cy+r*.20f,String.format(Locale.getDefault(),"%02d:%02d:%02d.%d",h,m,s,t),"");button(c,cx-r*.30f,cy+r*.55f,r*.27f,if(stopwatchRunning)"PAUSE" else "START");button(c,cx+r*.30f,cy+r*.55f,r*.27f,"RESET")}else text(c,String.format(Locale.getDefault(),"%02d:%02d:%02d",h,m,s),cx,cy+r*.25f,r*.12f,softWhite);second(c,cx,cy,r,(sec%60)*6f);hub(c,cx,cy,r)}
    private fun info(c:Canvas,x:Float,y:Float,title:String,value:String,suffix:String){val r=min(width,height)*.48f;paint.style=Paint.Style.FILL;paint.color=panel;c.drawRoundRect(x-r*.23f,y-r*.20f,x+r*.23f,y+r*.20f,r*.04f,r*.04f,paint);paint.style=Paint.Style.STROKE;paint.color=Color.rgb(60,65,60);paint.strokeWidth=r*.012f;c.drawRoundRect(x-r*.23f,y-r*.20f,x+r*.23f,y+r*.20f,r*.04f,r*.04f,paint);text(c,title,x,y-r*.07f,r*.06f,Color.LTGRAY);text(c,value,x,y+r*.06f,r*.12f,lime);if(suffix.isNotEmpty())text(c,suffix,x,y+r*.15f,r*.05f,Color.LTGRAY)}
    private fun digital(c:Canvas,cx:Float,cy:Float,value:String,sub:String){val r=min(width,height)*.48f;paint.style=Paint.Style.FILL;paint.color=panel;c.drawRoundRect(cx-r*.52f,cy-r*.18f,cx+r*.52f,cy+r*.18f,r*.04f,r*.04f,paint);paint.style=Paint.Style.STROKE;paint.color=Color.rgb(85,105,20);paint.strokeWidth=r*.008f;c.drawRoundRect(cx-r*.52f,cy-r*.18f,cx+r*.52f,cy+r*.18f,r*.04f,r*.04f,paint);text(c,value,cx,cy+r*.03f,r*.155f,softWhite);if(sub.isNotEmpty())text(c,sub,cx,cy+r*.13f,r*.052f,Color.LTGRAY)}
    private fun button(c:Canvas,cx:Float,cy:Float,hw:Float,label:String){val r=min(width,height)*.48f;paint.style=Paint.Style.FILL;paint.color=Color.rgb(20,23,20);c.drawRoundRect(cx-hw,cy-r*.12f,cx+hw,cy+r*.12f,r*.04f,r*.04f,paint);paint.style=Paint.Style.STROKE;paint.color=Color.rgb(75,80,75);paint.strokeWidth=r*.01f;c.drawRoundRect(cx-hw,cy-r*.12f,cx+hw,cy+r*.12f,r*.04f,r*.04f,paint);text(c,label,cx,cy+r*.025f,r*.07f,lime)}
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int){paint.style=Paint.Style.FILL;paint.typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD);paint.textAlign=Paint.Align.CENTER;paint.textSize=size;paint.color=color;c.drawText(s,x,y,paint)}
    private fun drawHand(c:Canvas,cx:Float,cy:Float,len:Float,deg:Float,w:Float){val a=Math.toRadians((deg-90).toDouble());paint.style=Paint.Style.STROKE;paint.color=softWhite;paint.strokeWidth=w;paint.strokeCap=Paint.Cap.SQUARE;c.drawLine(cx,cy,cx+cos(a).toFloat()*len,cy+sin(a).toFloat()*len,paint)}
    private fun second(c:Canvas,cx:Float,cy:Float,r:Float,deg:Float){val a=Math.toRadians((deg-90).toDouble());paint.style=Paint.Style.STROKE;paint.color=if(ambient)softWhite else lime;paint.strokeWidth=r*.018f;val tip=r*.88f;val tail=r*.12f;c.drawLine(cx-cos(a).toFloat()*tail,cy-sin(a).toFloat()*tail,cx+cos(a).toFloat()*tip,cy+sin(a).toFloat()*tip,paint)}
    private fun hub(c:Canvas,cx:Float,cy:Float,r:Float){paint.style=Paint.Style.FILL;paint.color=Color.BLACK;c.drawCircle(cx,cy,r*.045f,paint);paint.style=Paint.Style.STROKE;paint.color=if(ambient)softWhite else lime;paint.strokeWidth=r*.018f;c.drawCircle(cx,cy,r*.045f,paint)}
}

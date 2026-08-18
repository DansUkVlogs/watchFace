package uk.dansvlogs.clinicalwatch

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.*
import android.os.*
import android.util.Base64
import android.view.*
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class ClinicalWatchView(context: Context) : View(context), SensorEventListener {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG); private val h=Handler(Looper.getMainLooper())
    private val sm=context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor=sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER); private val hrSensor=sm.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private val prefs=context.getSharedPreferences("clinical_watch",Context.MODE_PRIVATE)
    private var ambient=false; private var battery=0; private var steps:Long?=null; private var pulse:Int?=null
    private var mode=0; private var running=false; private var base=0L; private var elapsed=0L
    private val lime=Color.rgb(190,235,0); private val ivory=Color.rgb(232,237,216); private val dark=Color.rgb(7,9,8)
    private val crest:Bitmap?=try{val b=Base64.decode(CrestData.PNG_BASE64,Base64.DEFAULT);BitmapFactory.decodeByteArray(b,0,b.size)}catch(_:Exception){null}
    private val ticker=object:Runnable{override fun run(){invalidate();h.postDelayed(this,if(mode==1&&running&&!ambient)100 else 1000)}}
    private val gd=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){override fun onDown(e:MotionEvent)=true;override fun onDoubleTap(e:MotionEvent):Boolean{val dx=e.x-width/2;val dy=e.y-height/2;if(dx*dx+dy*dy<(min(width,height)*.34f).pow(2)){mode=1-mode;restart();return true};return false}})
    private val br=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){val l=i?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1;val s=i?.getIntExtra(BatteryManager.EXTRA_SCALE,-1)?:-1;if(l>=0&&s>0)battery=l*100/s}}
    init{context.registerReceiver(br,IntentFilter(Intent.ACTION_BATTERY_CHANGED));h.post(ticker)}
    fun setAmbient(v:Boolean){ambient=v;restart()}
    fun startSensors(){
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED)stepSensor?.let{sm.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL)}
        val body=ContextCompat.checkSelfPermission(context,Manifest.permission.BODY_SENSORS)==PackageManager.PERMISSION_GRANTED
        val health=if(Build.VERSION.SDK_INT>=36)ContextCompat.checkSelfPermission(context,"android.permission.health.READ_HEART_RATE")==PackageManager.PERMISSION_GRANTED else true
        if(body||health)hrSensor?.let{sm.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL)}
    }
    fun stopSensors(){/* Keep listeners while this watch-style app is ambient; detached window cleans up. */}
    override fun onSensorChanged(e:SensorEvent){
        if(e.sensor.type==Sensor.TYPE_STEP_COUNTER){val raw=e.values[0].toLong();val day=SimpleDateFormat("yyyyMMdd",Locale.US).format(Date());val savedDay=prefs.getString("day","");var zero=prefs.getLong("zero",-1);if(savedDay!=day||zero<0||raw<zero){zero=raw;prefs.edit().putString("day",day).putLong("zero",zero).apply()};steps=(raw-zero).coerceAtLeast(0)}
        if(e.sensor.type==Sensor.TYPE_HEART_RATE){val v=e.values[0].roundToInt();if(v in 25..240)pulse=v};invalidate()
    }
    override fun onAccuracyChanged(s:Sensor?,a:Int){}
    private fun restart(){h.removeCallbacks(ticker);h.post(ticker)}
    override fun onTouchEvent(e:MotionEvent):Boolean{if(gd.onTouchEvent(e))return true;if(e.action==MotionEvent.ACTION_UP&&mode==1&&!ambient&&e.y>height*.66f&&e.y<height*.88f){if(e.x<width/2)toggle()else reset();return true};return true}
    private fun toggle(){if(running){elapsed=nowElapsed();running=false}else{base=SystemClock.elapsedRealtime()-elapsed;running=true};restart()}
    private fun reset(){running=false;elapsed=0;base=SystemClock.elapsedRealtime();restart()}
    private fun nowElapsed()=if(running)SystemClock.elapsedRealtime()-base else elapsed
    override fun onDetachedFromWindow(){sm.unregisterListener(this);h.removeCallbacks(ticker);try{context.unregisterReceiver(br)}catch(_:Exception){};super.onDetachedFromWindow()}

    override fun onDraw(c:Canvas){c.drawColor(Color.BLACK);val cx=width/2f;val cy=height/2f;val r=min(width,height)*.495f;dial(c,cx,cy,r);if(mode==0)clock(c,cx,cy,r)else stopwatch(c,cx,cy,r)}
    private fun dial(c:Canvas,cx:Float,cy:Float,r:Float){
        p.style=Paint.Style.FILL;p.color=Color.rgb(3,4,4);c.drawCircle(cx,cy,r*.96f,p);p.style=Paint.Style.STROKE
        for(j in 0..2){p.color=Color.rgb(22+j*9,24+j*9,23+j*9);p.strokeWidth=r*.012f;c.drawCircle(cx,cy,r*(.94f-j*.055f),p)}
        for(i in 0 until 60){val a=Math.toRadians((i*6-90).toDouble());val major=i%5==0;val outer=r*.91f;val inner=outer-r*(if(major).10f else .045f);p.color=if(i%15==0&&!ambient)lime else ivory;p.strokeWidth=r*(if(major).032f else .009f);c.drawLine(cx+cos(a).toFloat()*inner,cy+sin(a).toFloat()*inner,cx+cos(a).toFloat()*outer,cy+sin(a).toFloat()*outer,p)}
        val rr=r*.79f;txt(c,"60",cx,cy-rr+r*.035f,r*.105f,if(ambient)ivory else lime);txt(c,"15",cx+rr,cy+r*.035f,r*.10f,if(ambient)ivory else lime);txt(c,"30",cx,cy+rr+r*.035f,r*.10f,if(ambient)ivory else lime);txt(c,"45",cx-rr,cy+r*.035f,r*.10f,if(ambient)ivory else lime)
    }
    private fun crest(c:Canvas,cx:Float,top:Float,r:Float){if(crest!=null){val hh=r*.30f;val ww=hh*crest.width/crest.height;val dst=RectF(cx-ww/2,top,cx+ww/2,top+hh);p.alpha=255;c.drawBitmap(crest,null,dst,p);p.alpha=255}}
    private fun clock(c:Canvas,cx:Float,cy:Float,r:Float){val cal=Calendar.getInstance();val s=cal.get(Calendar.SECOND);val m=cal.get(Calendar.MINUTE);val hr=cal.get(Calendar.HOUR);if(!ambient){crest(c,cx,cy-r*.69f,r);txt(c,"PARAMEDIC",cx,cy-r*.34f,r*.070f,ivory);txt(c,"DAN",cx,cy-r*.235f,r*.105f,lime);panel(c,cx-r*.54f,cy-r*.02f,"STEPS",steps?.let{"%,d".format(it)}?:"--","");panel(c,cx+r*.54f,cy-r*.02f,"PULSE",pulse?.toString()?:"--","BPM");digital(c,cx,cy+r*.35f,SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(Date()),SimpleDateFormat("EEE  d MMM yyyy",Locale.getDefault()).format(Date()).uppercase());txt(c,"WATCH  $battery%",cx,cy+r*.69f,r*.060f,lime)};val mv=m+s/60f;hand(c,cx,cy,r*.45f,(hr+mv/60)*30,r*.075f);hand(c,cx,cy,r*.67f,mv*6,r*.055f);second(c,cx,cy,r,s*6f);hub(c,cx,cy,r)}
    private fun stopwatch(c:Canvas,cx:Float,cy:Float,r:Float){val e=nowElapsed();val sec=e/1000;val t=(e/100)%10;val ss=sec%60;val mm=(sec/60)%60;val hh=sec/3600;if(!ambient){crest(c,cx,cy-r*.69f,r);txt(c,"PARAMEDIC",cx,cy-r*.34f,r*.070f,ivory);txt(c,"DAN",cx,cy-r*.235f,r*.105f,lime);txt(c,"STOPWATCH",cx,cy+r*.02f,r*.065f,Color.LTGRAY);digital(c,cx,cy+r*.22f,String.format(Locale.getDefault(),"%02d:%02d:%02d.%d",hh,mm,ss,t),"");button(c,cx-r*.30f,cy+r*.56f,r*.27f,if(running)"PAUSE" else "START");button(c,cx+r*.30f,cy+r*.56f,r*.27f,"RESET")}else txt(c,String.format(Locale.getDefault(),"%02d:%02d:%02d",hh,mm,ss),cx,cy+r*.25f,r*.12f,ivory);second(c,cx,cy,r,(sec%60)*6f);hub(c,cx,cy,r)}
    private fun panel(c:Canvas,x:Float,y:Float,title:String,value:String,suffix:String){val r=min(width,height)*.495f;p.style=Paint.Style.FILL;p.color=dark;c.drawRoundRect(x-r*.22f,y-r*.20f,x+r*.22f,y+r*.20f,r*.04f,r*.04f,p);p.style=Paint.Style.STROKE;p.color=Color.rgb(95,100,94);p.strokeWidth=r*.012f;c.drawRoundRect(x-r*.22f,y-r*.20f,x+r*.22f,y+r*.20f,r*.04f,r*.04f,p);txt(c,title,x,y-r*.07f,r*.058f,Color.LTGRAY);txt(c,value,x,y+r*.065f,r*.12f,lime);if(suffix.isNotEmpty())txt(c,suffix,x,y+r*.15f,r*.05f,Color.LTGRAY)}
    private fun digital(c:Canvas,x:Float,y:Float,v:String,sub:String){val r=min(width,height)*.495f;p.style=Paint.Style.FILL;p.color=dark;c.drawRoundRect(x-r*.51f,y-r*.17f,x+r*.51f,y+r*.17f,r*.04f,r*.04f,p);p.style=Paint.Style.STROKE;p.color=Color.rgb(105,130,5);p.strokeWidth=r*.009f;c.drawRoundRect(x-r*.51f,y-r*.17f,x+r*.51f,y+r*.17f,r*.04f,r*.04f,p);txt(c,v,x,y+r*.025f,r*.15f,ivory);if(sub.isNotEmpty())txt(c,sub,x,y+r*.13f,r*.05f,Color.LTGRAY)}
    private fun button(c:Canvas,x:Float,y:Float,hw:Float,s:String){val r=min(width,height)*.495f;p.style=Paint.Style.FILL;p.color=Color.rgb(18,21,19);c.drawRoundRect(x-hw,y-r*.12f,x+hw,y+r*.12f,r*.04f,r*.04f,p);p.style=Paint.Style.STROKE;p.color=Color.rgb(70,75,72);p.strokeWidth=r*.012f;c.drawRoundRect(x-hw,y-r*.12f,x+hw,y+r*.12f,r*.04f,r*.04f,p);txt(c,s,x,y+r*.025f,r*.07f,lime)}
    private fun txt(c:Canvas,s:String,x:Float,y:Float,size:Float,col:Int){p.style=Paint.Style.FILL;p.typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD);p.textAlign=Paint.Align.CENTER;p.textSize=size;p.color=col;c.drawText(s,x,y,p)}
    private fun hand(c:Canvas,cx:Float,cy:Float,len:Float,d:Float,w:Float){val a=Math.toRadians((d-90).toDouble());p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.SQUARE;p.strokeWidth=w;p.color=Color.rgb(45,48,46);c.drawLine(cx,cy,cx+cos(a).toFloat()*len,cy+sin(a).toFloat()*len,p);p.strokeWidth=w*.62f;p.color=ivory;c.drawLine(cx,cy,cx+cos(a).toFloat()*len,cy+sin(a).toFloat()*len,p)}
    private fun second(c:Canvas,cx:Float,cy:Float,r:Float,d:Float){val a=Math.toRadians((d-90).toDouble());p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.color=if(ambient)ivory else lime;p.strokeWidth=r*.017f;c.drawLine(cx-cos(a).toFloat()*r*.13f,cy-sin(a).toFloat()*r*.13f,cx+cos(a).toFloat()*r*.86f,cy+sin(a).toFloat()*r*.86f,p)}
    private fun hub(c:Canvas,cx:Float,cy:Float,r:Float){p.style=Paint.Style.FILL;p.color=Color.BLACK;c.drawCircle(cx,cy,r*.047f,p);p.style=Paint.Style.STROKE;p.color=if(ambient)ivory else lime;p.strokeWidth=r*.018f;c.drawCircle(cx,cy,r*.047f,p)}
}

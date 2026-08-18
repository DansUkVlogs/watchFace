package uk.dansvlogs.clinicalwatch

import android.content.*
import android.graphics.*
import android.os.*
import android.util.Base64
import android.view.*
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
    private var steps: Long? = null
    private var name = "DAN"
    private var accent = Color.rgb(190, 230, 0)
    private val white = Color.rgb(242, 244, 238)
    private var customImage: Bitmap? = null
    private var stopwatchMode = false
    private var stopwatchRunning = false
    private var stopwatchBase = 0L
    private var stopwatchHeld = 0L

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onDoubleTap(e: MotionEvent): Boolean { stopwatchMode = !stopwatchMode; restart(); invalidate(); return true }
    })
    private val dataReceiver = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { load(); invalidate() } }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val l=i?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1; val s=i?.getIntExtra(BatteryManager.EXTRA_SCALE,-1)?:-1
            if(l>=0&&s>0) watchBattery=l*100/s; invalidate()
        }
    }
    private val ticker=object:Runnable{override fun run(){invalidate();handler.postDelayed(this,if(stopwatchMode&&stopwatchRunning&&!ambient)100L else 1000L)}}

    init {
        context.registerReceiver(batteryReceiver,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ContextCompat.registerReceiver(context,dataReceiver,IntentFilter("uk.dansvlogs.clinicalwatch.DATA_CHANGED"),ContextCompat.RECEIVER_NOT_EXPORTED)
        load(); handler.post(ticker)
    }
    private fun load(){
        name=prefs.getString("name","DAN")?:"DAN"
        try{accent=Color.parseColor(prefs.getString("accent","#BEE600"))}catch(_:Throwable){}
        phoneBattery=prefs.getString("phoneBattery",null)?.toIntOrNull()?:phoneBattery
        steps=prefs.getString("steps",null)?.toLongOrNull()?:steps
        customImage=try{prefs.getString("crest",null)?.let{val b=Base64.decode(it,Base64.DEFAULT);BitmapFactory.decodeByteArray(b,0,b.size)}}catch(_:Throwable){null}
    }
    fun setAmbient(v:Boolean){ambient=v;restart()}
    fun setExternalSteps(v:Long){steps=v.coerceAtLeast(0);invalidate()}
    fun startSensors(){}
    fun stopSensors(){}
    private fun restart(){handler.removeCallbacks(ticker);handler.post(ticker)}
    private fun stopwatchMs()=if(stopwatchRunning)SystemClock.elapsedRealtime()-stopwatchBase else stopwatchHeld
    private fun toggleStopwatch(){if(stopwatchRunning){stopwatchHeld=stopwatchMs();stopwatchRunning=false}else{stopwatchBase=SystemClock.elapsedRealtime()-stopwatchHeld;stopwatchRunning=true};restart();invalidate()}
    private fun resetStopwatch(){stopwatchRunning=false;stopwatchHeld=0;stopwatchBase=SystemClock.elapsedRealtime();restart();invalidate()}

    override fun onTouchEvent(e:MotionEvent):Boolean{
        if(gestures.onTouchEvent(e)) return true
        if(e.action==MotionEvent.ACTION_UP&&stopwatchMode&&!ambient){val cx=width/2f;val cy=height/2f;val r=min(width,height)*.495f;if(e.y in (cy-r*.20f)..(cy+r*.24f)){if(e.x<cx-r*.12f)toggleStopwatch() else if(e.x>cx+r*.12f)resetStopwatch();return true}}
        return true
    }
    override fun onDetachedFromWindow(){handler.removeCallbacks(ticker);try{context.unregisterReceiver(batteryReceiver)}catch(_:Exception){};try{context.unregisterReceiver(dataReceiver)}catch(_:Exception){};super.onDetachedFromWindow()}

    override fun onDraw(c:Canvas){super.onDraw(c);c.drawColor(Color.BLACK);val cx=width/2f;val cy=height/2f;val r=min(width,height)*.495f;background(c,cx,cy,r);dial(c,cx,cy,r);if(!ambient){top(c,cx,cy,r);if(stopwatchMode)stopwatchInfo(c,cx,cy,r)else clockInfo(c,cx,cy,r)};hands(c,cx,cy,r)}

    private fun background(c:Canvas,cx:Float,cy:Float,r:Float){paint.style=Paint.Style.FILL;paint.shader=RadialGradient(cx,cy,r,intArrayOf(Color.rgb(29,31,31),Color.rgb(10,11,12),Color.BLACK),floatArrayOf(0f,.72f,1f),Shader.TileMode.CLAMP);c.drawCircle(cx,cy,r,paint);paint.shader=null;paint.style=Paint.Style.STROKE;paint.color=Color.rgb(65,70,69);paint.strokeWidth=r*.020f;c.drawCircle(cx,cy,r*.958f,paint);paint.color=Color.rgb(120,124,120);paint.strokeWidth=r*.004f;c.drawCircle(cx,cy,r*.900f,paint)}
    private fun dial(c:Canvas,cx:Float,cy:Float,r:Float){for(i in 0 until 60){if(i==0)continue;val a=Math.toRadians((i*6-90).toDouble());val major=i%5==0;val outer=r*.882f;val inner=outer-r*(if(major).090f else .038f);paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.strokeWidth=r*(if(major).018f else .0055f);paint.color=if(i%15==0&&!ambient)accent else white;c.drawLine(cx+cos(a).toFloat()*inner,cy+sin(a).toFloat()*inner,cx+cos(a).toFloat()*outer,cy+sin(a).toFloat()*outer,paint)}}
    private fun top(c:Canvas,cx:Float,cy:Float,r:Float){drawImageCircle(c,cx,cy-r*.755f,r*.105f);text(c,name.uppercase(Locale.getDefault()),cx,cy-r*.565f,r*.090f,white,true);if(!stopwatchMode){val now=Date();val date=SimpleDateFormat("EEE dd MMM(MM) yyyy",Locale.getDefault()).format(now).uppercase(Locale.getDefault());text(c,date,cx,cy-r*.395f,r*.062f,white,true)}}
    private fun drawImageCircle(c:Canvas,x:Float,y:Float,rad:Float){paint.style=Paint.Style.FILL;paint.color=Color.rgb(10,12,12);c.drawCircle(x,y,rad,paint);customImage?.let{c.save();val clip=Path().apply{addCircle(x,y,rad*.84f,Path.Direction.CW)};c.clipPath(clip);val scale=max(rad*1.68f/it.width,rad*1.68f/it.height);val w=it.width*scale;val h=it.height*scale;c.drawBitmap(it,null,RectF(x-w/2,y-h/2,x+w/2,y+h/2),paint);c.restore()};paint.style=Paint.Style.STROKE;paint.strokeWidth=rad*.10f;paint.color=accent;c.drawCircle(x,y,rad,paint)}

    private fun clockInfo(c:Canvas,cx:Float,cy:Float,r:Float){
        // Three independent zones around the pivot: batteries left, steps right, digital below.
        sidePanel(c,cx-r*.54f,cy-r*.055f,r*.255f,r*.205f);watchIcon(c,cx-r*.625f,cy-r*.115f,r*.055f);text(c,"$watchBattery%",cx-r*.475f,cy-r*.095f,r*.060f,white,true);phoneIcon(c,cx-r*.625f,cy+r*.055f,r*.050f);text(c,phoneBattery?.let{"$it%"}?:"--",cx-r*.475f,cy+r*.075f,r*.060f,white,true)
        sidePanel(c,cx+r*.54f,cy-r*.055f,r*.255f,r*.205f);stepsIcon(c,cx+r*.455f,cy-r*.055f,r*.060f);text(c,steps?.let{"%,d".format(it)}?:"--",cx+r*.565f,cy+r*.055f,r*.080f,accent,true)
        digitalPanel(c,cx,cy+r*.315f,r);text(c,SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(Date()),cx,cy+r*.365f,r*.145f,white,true)
    }
    private fun stopwatchInfo(c:Canvas,cx:Float,cy:Float,r:Float){
        actionPanel(c,cx-r*.53f,cy-r*.055f,r*.27f,r*.205f);playPauseIcon(c,cx-r*.53f,cy-r*.055f,r*.075f,stopwatchRunning)
        actionPanel(c,cx+r*.53f,cy-r*.055f,r*.27f,r*.205f);resetIcon(c,cx+r*.53f,cy-r*.055f,r*.075f)
        digitalPanel(c,cx,cy+r*.315f,r);val ms=stopwatchMs();val total=ms/1000;val tenths=(ms/100)%10;val sec=total%60;val min=(total/60)%60;val hr=total/3600;text(c,String.format(Locale.getDefault(),"%02d:%02d:%02d:%d",hr,min,sec,tenths),cx,cy+r*.365f,r*.125f,white,true)
    }
    private fun digitalPanel(c:Canvas,x:Float,y:Float,r:Float){panel(c,x-r*.47f,y-r*.16f,x+r*.47f,y+r*.16f,r*.045f,true)}
    private fun sidePanel(c:Canvas,x:Float,y:Float,halfW:Float,halfH:Float){panel(c,x-halfW,y-halfH,x+halfW,y+halfH,halfH*.16f,false)}
    private fun actionPanel(c:Canvas,x:Float,y:Float,halfW:Float,halfH:Float){panel(c,x-halfW,y-halfH,x+halfW,y+halfH,halfH*.16f,true)}
    private fun panel(c:Canvas,l:Float,t:Float,rr:Float,b:Float,rad:Float,highlight:Boolean){paint.style=Paint.Style.FILL;paint.color=Color.argb(235,5,7,8);c.drawRoundRect(l,t,rr,b,rad,rad,paint);paint.style=Paint.Style.STROKE;paint.strokeWidth=min(width,height)*.004f;paint.color=if(highlight)accent else Color.rgb(75,80,78);c.drawRoundRect(l,t,rr,b,rad,rad,paint)}

    private fun hands(c:Canvas,cx:Float,cy:Float,r:Float){val cal=Calendar.getInstance();if(stopwatchMode){val seconds=(stopwatchMs()/1000)%60;secondHand(c,cx,cy,r,seconds*6f)}else{val sec=cal.get(Calendar.SECOND);val min=cal.get(Calendar.MINUTE);val hour=cal.get(Calendar.HOUR);hand(c,cx,cy,r*.43f,(hour+min/60f)*30f,r*.070f);hand(c,cx,cy,r*.64f,(min+sec/60f)*6f,r*.050f);if(!ambient)secondHand(c,cx,cy,r,sec*6f)};hub(c,cx,cy,r)}
    private fun hand(c:Canvas,cx:Float,cy:Float,len:Float,d:Float,w:Float){val a=Math.toRadians((d-90).toDouble());val ex=cx+cos(a).toFloat()*len;val ey=cy+sin(a).toFloat()*len;paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.color=Color.BLACK;paint.strokeWidth=w*1.55f;c.drawLine(cx,cy,ex,ey,paint);paint.color=Color.rgb(105,110,108);paint.strokeWidth=w*1.18f;c.drawLine(cx,cy,ex,ey,paint);paint.color=white;paint.strokeWidth=w*.72f;c.drawLine(cx,cy,ex,ey,paint)}
    private fun secondHand(c:Canvas,cx:Float,cy:Float,r:Float,d:Float){val a=Math.toRadians((d-90).toDouble());paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.strokeWidth=r*.014f;paint.color=accent;c.drawLine(cx-cos(a).toFloat()*r*.12f,cy-sin(a).toFloat()*r*.12f,cx+cos(a).toFloat()*r*.76f,cy+sin(a).toFloat()*r*.76f,paint)}
    private fun hub(c:Canvas,cx:Float,cy:Float,r:Float){paint.style=Paint.Style.FILL;paint.color=Color.rgb(15,17,17);c.drawCircle(cx,cy,r*.060f,paint);paint.style=Paint.Style.STROKE;paint.strokeWidth=r*.017f;paint.color=accent;c.drawCircle(cx,cy,r*.040f,paint);paint.style=Paint.Style.FILL;paint.color=white;c.drawCircle(cx,cy,r*.012f,paint)}

    private fun watchIcon(c:Canvas,x:Float,y:Float,s:Float){paint.style=Paint.Style.STROKE;paint.strokeWidth=s*.16f;paint.strokeCap=Paint.Cap.ROUND;paint.color=accent;c.drawRoundRect(x-s*.55f,y-s*.45f,x+s*.55f,y+s*.45f,s*.18f,s*.18f,paint);c.drawLine(x-s*.25f,y-s*.75f,x+s*.25f,y-s*.75f,paint);c.drawLine(x-s*.25f,y+s*.75f,x+s*.25f,y+s*.75f,paint)}
    private fun phoneIcon(c:Canvas,x:Float,y:Float,s:Float){paint.style=Paint.Style.STROKE;paint.strokeWidth=s*.16f;paint.color=accent;c.drawRoundRect(x-s*.42f,y-s*.72f,x+s*.42f,y+s*.72f,s*.15f,s*.15f,paint);c.drawCircle(x,y+s*.52f,s*.055f,paint)}
    private fun stepsIcon(c:Canvas,x:Float,y:Float,s:Float){paint.style=Paint.Style.STROKE;paint.strokeWidth=s*.18f;paint.strokeCap=Paint.Cap.ROUND;paint.color=accent;c.drawOval(RectF(x-s*.58f,y-s*.65f,x+s*.05f,y+s*.20f),paint);c.drawOval(RectF(x-s*.02f,y-s*.05f,x+s*.58f,y+s*.72f),paint)}
    private fun playPauseIcon(c:Canvas,x:Float,y:Float,s:Float,paused:Boolean){paint.style=Paint.Style.FILL;paint.color=accent;if(paused){c.drawRect(x-s*.42f,y-s*.55f,x-s*.12f,y+s*.55f,paint);c.drawRect(x+s*.12f,y-s*.55f,x+s*.42f,y+s*.55f,paint)}else{val path=Path().apply{moveTo(x-s*.40f,y-s*.60f);lineTo(x+s*.55f,y);lineTo(x-s*.40f,y+s*.60f);close()};c.drawPath(path,paint)}}
    private fun resetIcon(c:Canvas,x:Float,y:Float,s:Float){paint.style=Paint.Style.STROKE;paint.strokeWidth=s*.18f;paint.strokeCap=Paint.Cap.ROUND;paint.color=accent;val rect=RectF(x-s*.58f,y-s*.58f,x+s*.58f,y+s*.58f);c.drawArc(rect,-55f,300f,false,paint);paint.style=Paint.Style.FILL;val path=Path().apply{moveTo(x+s*.20f,y-s*.72f);lineTo(x+s*.68f,y-s*.72f);lineTo(x+s*.58f,y-s*.25f);close()};c.drawPath(path,paint)}
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,col:Int,bold:Boolean){paint.shader=null;paint.style=Paint.Style.FILL;paint.typeface=Typeface.create("sans-serif-condensed",if(bold)Typeface.BOLD else Typeface.NORMAL);paint.textAlign=Paint.Align.CENTER;paint.textSize=size;paint.color=col;c.drawText(s,x,y,paint)}
}

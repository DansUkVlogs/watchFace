package uk.dansvlogs.clinicalwatch.companion

import android.app.Activity
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.*
import com.google.android.gms.wearable.Wearable
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime

class MainActivity : Activity() {
    private lateinit var name: EditText; private lateinit var status: TextView; private lateinit var stepsView: TextView; private lateinit var preview: ImageView
    private var accent="#BEEB00"; private var crestUri:Uri?=null; private var healthSteps=0L
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main); private val health by lazy{HealthDataService.getStore(applicationContext)}; private val stepPermission=Permission.of(DataTypes.STEPS,AccessType.READ)
    private val lime get()=Color.parseColor("#BEEB00"); private val panel=Color.rgb(8,11,9); private val silver=Color.rgb(205,210,205)
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun box(stroke:Int=lime,r:Int=12)=GradientDrawable().apply{shape=GradientDrawable.RECTANGLE;setColor(panel);cornerRadius=dp(r).toFloat();setStroke(dp(1),stroke)}
    private fun txt(s:String,z:Float=16f,col:Int=silver,bold:Boolean=false)=TextView(this).apply{text=s;textSize=z;setTextColor(col);typeface=Typeface.create("sans-serif-condensed",if(bold)Typeface.BOLD else Typeface.NORMAL);letterSpacing=.05f}
    private fun button(s:String,action:()->Unit)=Button(this).apply{text=s;textSize=14f;setTextColor(lime);typeface=Typeface.DEFAULT_BOLD;background=box();setPadding(dp(12),dp(8),dp(12),dp(8));setOnClickListener{action()}}
    private fun section(title:String,body:LinearLayout.()->Unit)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=box();setPadding(dp(16),dp(14),dp(16),dp(14));addView(txt(title,20f,lime,true));body();layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,dp(12))}}
    override fun onCreate(b:Bundle?){super.onCreate(b);window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(18),dp(14),dp(24));setBackgroundColor(Color.BLACK)}
        root.addView(txt("CLINICALWATCH",32f,Color.WHITE,true));root.addView(txt("COMPANION",20f,lime,true),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(18)})
        root.addView(section("♥  SAMSUNG HEALTH"){
            status=txt("Tap Connect to allow step access",15f);addView(status)
            val row=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            val stat=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;addView(txt("TODAY'S STEPS",13f));stepsView=txt("--",38f,lime,true);addView(stepsView)}
            row.addView(stat,LinearLayout.LayoutParams(0,-2,1f));row.addView(button("CONNECT / REFRESH\nSAMSUNG HEALTH"){connectHealth()},LinearLayout.LayoutParams(dp(170),dp(92)));addView(row)
        })
        root.addView(section("✎  WATCH APPEARANCE"){
            addView(txt("CUSTOM TEXT (NAME)",13f));name=EditText(this@MainActivity).apply{setText("DAN");textSize=26f;setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);background=box();setPadding(dp(14),dp(8),dp(14),dp(8))};addView(name,LinearLayout.LayoutParams(-1,dp(62)).apply{bottomMargin=dp(12)})
            addView(txt("ACCENT COLOUR",13f));val colours=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL};listOf("#BEEB00","#00E5FF","#FF3B30","#FF9500","#FFFFFF").forEach{hex->colours.addView(Button(this@MainActivity).apply{text="";background=GradientDrawable().apply{cornerRadius=dp(7).toFloat();setColor(Color.parseColor(hex));setStroke(dp(2),Color.rgb(35,40,35))};setOnClickListener{accent=hex}},LinearLayout.LayoutParams(0,dp(54),1f).apply{setMargins(dp(3),0,dp(3),0)})};addView(colours)
        })
        root.addView(section("♢  CUSTOM CREST / SHIELD"){
            val row=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
            preview=ImageView(this@MainActivity).apply{adjustViewBounds=true;scaleType=ImageView.ScaleType.CENTER_INSIDE;background=box(Color.rgb(70,75,70))};row.addView(preview,LinearLayout.LayoutParams(0,dp(170),1f).apply{rightMargin=dp(10)});row.addView(button("↑\nCHOOSE CREST /\nSHIELD PNG"){pickCrest()},LinearLayout.LayoutParams(0,dp(170),1f));addView(row)
        })
        root.addView(button("⟳   SYNC EVERYTHING TO WATCH"){syncAll()},LinearLayout.LayoutParams(-1,dp(72)).apply{bottomMargin=dp(12)});root.addView(txt("Samsung Health's combined daily steps, phone battery, text, accent and crest are sent to the watch.",14f,Color.GRAY).apply{gravity=Gravity.CENTER})
        setContentView(ScrollView(this).apply{setBackgroundColor(Color.BLACK);addView(root)});scope.launch{delay(500);refreshIfPermitted()}
    }
    private fun connectHealth()=scope.launch{try{status.text="Connecting to Samsung Health…";var granted=health.getGrantedPermissions(setOf(stepPermission));if(!granted.contains(stepPermission))granted=health.requestPermissions(setOf(stepPermission),this@MainActivity);if(granted.contains(stepPermission))readTodaySteps()else status.text="Samsung Health step permission was not granted"}catch(t:Throwable){status.text="Samsung Health error: ${t.message?:t.javaClass.simpleName}"}}
    private fun refreshIfPermitted()=scope.launch{try{if(health.getGrantedPermissions(setOf(stepPermission)).contains(stepPermission))readTodaySteps()}catch(_:Throwable){}}
    private suspend fun readTodaySteps(){val request=DataType.StepsType.TOTAL.requestBuilder.setLocalTimeFilter(LocalTimeFilter.of(LocalDate.now().atStartOfDay(),LocalDateTime.now())).build();val result=health.aggregateData(request);healthSteps=result.dataList.firstOrNull()?.value?:0L;stepsView.text="%,d".format(healthSteps);status.text="Samsung Health connected";sendLiveData()}
    private fun pickCrest(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/png"},10)}
    override fun onActivityResult(r:Int,result:Int,data:Intent?){super.onActivityResult(r,result,data);if(r==10&&result==RESULT_OK){crestUri=data?.data;crestUri?.let{try{contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Throwable){};preview.setImageURI(it)}}}
    private fun phoneBattery():Int{val bm=getSystemService(BATTERY_SERVICE) as BatteryManager;return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0,100)}
    private fun send(path:String,bytes:ByteArray){Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener{nodes->nodes.forEach{Wearable.getMessageClient(this).sendMessage(it.id,path,bytes)};status.text=if(nodes.isEmpty())"Samsung Health OK • no connected watch found" else "Samsung Health OK • synced to watch"}}
    private fun sendLiveData(){send("/clinicalwatch/config","steps=$healthSteps\nphoneBattery=${phoneBattery()}".toByteArray())}
    private fun syncAll(){send("/clinicalwatch/config","name=${name.text.toString().trim().ifEmpty{"DAN"}}\naccent=$accent\nsteps=$healthSteps\nphoneBattery=${phoneBattery()}".toByteArray());crestUri?.let{u->try{val src=contentResolver.openInputStream(u)?.use{BitmapFactory.decodeStream(it)}?:return@let;val scale=minOf(256f/src.width,256f/src.height,1f);val bmp=Bitmap.createScaledBitmap(src,(src.width*scale).toInt().coerceAtLeast(1),(src.height*scale).toInt().coerceAtLeast(1),true);val out=ByteArrayOutputStream();bmp.compress(Bitmap.CompressFormat.PNG,90,out);send("/clinicalwatch/crest",out.toByteArray())}catch(t:Throwable){status.text="Crest error: ${t.message}"}}}
    override fun onResume(){super.onResume();scope.launch{refreshIfPermitted()}}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}

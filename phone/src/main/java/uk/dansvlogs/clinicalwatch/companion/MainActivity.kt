package uk.dansvlogs.clinicalwatch.companion

import android.app.Activity
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.*
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
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private val health by lazy { HealthDataService.getStore(applicationContext) }
    private val stepPermission=Permission.of(DataTypes.STEPS,AccessType.READ)

    override fun onCreate(b:Bundle?){super.onCreate(b);val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(40,40,40,40);setBackgroundColor(Color.rgb(16,19,16))};fun title(s:String,z:Float=18f)=TextView(this).apply{text=s;textSize=z;setTextColor(Color.WHITE);setPadding(0,14,0,8)}
        root.addView(title("ClinicalWatch Companion",28f));root.addView(title("Samsung Health",20f));status=title("Tap Connect to allow step access",15f);root.addView(status);stepsView=title("Today's steps: --",18f);root.addView(stepsView)
        Button(this).apply{text="CONNECT / REFRESH SAMSUNG HEALTH";setOnClickListener{connectHealth()};root.addView(this)}
        root.addView(title("Watch appearance",20f));name=EditText(this).apply{hint="Displayed name";setText("DAN");setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);root.addView(this)}
        val colours=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};listOf("#BEEB00","#00E5FF","#FF3B30","#FF9500","#FFFFFF").forEach{hex->colours.addView(Button(this).apply{text=" ";setBackgroundColor(Color.parseColor(hex));setOnClickListener{accent=hex}},LinearLayout.LayoutParams(0,90,1f))};root.addView(colours)
        preview=ImageView(this).apply{adjustViewBounds=true;minimumHeight=180;root.addView(this)};Button(this).apply{text="CHOOSE CREST / SHIELD PNG";setOnClickListener{pickCrest()};root.addView(this)}
        Button(this).apply{text="SYNC EVERYTHING TO WATCH";setOnClickListener{syncAll()};root.addView(this)};root.addView(title("Samsung Health's combined daily steps, phone battery, name, accent and crest are sent to the watch.",14f));setContentView(ScrollView(this).apply{addView(root)});scope.launch{delay(500);refreshIfPermitted()}
    }
    private fun connectHealth()=scope.launch{try{status.text="Connecting to Samsung Health…";var granted=health.getGrantedPermissions(setOf(stepPermission));if(!granted.contains(stepPermission))granted=health.requestPermissions(setOf(stepPermission),this@MainActivity);if(granted.contains(stepPermission))readTodaySteps()else status.text="Samsung Health step permission was not granted"}catch(t:Throwable){status.text="Samsung Health error: ${t.message?:t.javaClass.simpleName}"}}
    private fun refreshIfPermitted()=scope.launch{try{if(health.getGrantedPermissions(setOf(stepPermission)).contains(stepPermission))readTodaySteps()}catch(_:Throwable){}}
    private suspend fun readTodaySteps(){val request=DataType.StepsType.TOTAL.requestBuilder.setLocalTimeFilter(LocalTimeFilter.of(LocalDate.now().atStartOfDay(),LocalDateTime.now())).build();val result=health.aggregateData(request);healthSteps=result.dataList.firstOrNull()?.value?:0L;stepsView.text="Today's steps: %,d".format(healthSteps);status.text="Samsung Health connected";sendLiveData()}
    private fun pickCrest(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/png"},10)}
    override fun onActivityResult(r:Int,result:Int,data:Intent?){super.onActivityResult(r,result,data);if(r==10&&result==RESULT_OK){crestUri=data?.data;crestUri?.let{try{contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Throwable){};preview.setImageURI(it)}}}
    private fun phoneBattery():Int{val bm=getSystemService(BATTERY_SERVICE) as BatteryManager;return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0,100)}
    private fun send(path:String,bytes:ByteArray){Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener{nodes->nodes.forEach{Wearable.getMessageClient(this).sendMessage(it.id,path,bytes)};status.text=if(nodes.isEmpty())"Samsung Health OK • no connected watch found" else "Samsung Health OK • synced to watch"}}
    private fun sendLiveData(){send("/clinicalwatch/config","steps=$healthSteps\nphoneBattery=${phoneBattery()}".toByteArray())}
    private fun syncAll(){send("/clinicalwatch/config","name=${name.text.toString().trim().ifEmpty{"DAN"}}\naccent=$accent\nsteps=$healthSteps\nphoneBattery=${phoneBattery()}".toByteArray());crestUri?.let{u->try{val src=contentResolver.openInputStream(u)?.use{BitmapFactory.decodeStream(it)}?:return@let;val scale=minOf(256f/src.width,256f/src.height,1f);val bmp=Bitmap.createScaledBitmap(src,(src.width*scale).toInt().coerceAtLeast(1),(src.height*scale).toInt().coerceAtLeast(1),true);val out=ByteArrayOutputStream();bmp.compress(Bitmap.CompressFormat.PNG,90,out);send("/clinicalwatch/crest",out.toByteArray())}catch(t:Throwable){status.text="Crest error: ${t.message}"}}}
    override fun onResume(){super.onResume();scope.launch{refreshIfPermitted()}}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}

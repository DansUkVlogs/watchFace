package uk.dansvlogs.clinicalwatch.companion

import android.app.Activity
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import com.google.android.gms.wearable.Wearable

class MainActivity : Activity() {
    private lateinit var name:EditText; private lateinit var status:TextView; private lateinit var preview:ImageView; private lateinit var steps:EditText
    private var accent="#BEEB00"; private var crestUri:Uri?=null
    override fun onCreate(b:Bundle?){super.onCreate(b);val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(40,40,40,40);setBackgroundColor(Color.rgb(16,19,16))};fun title(s:String,z:Float=18f)=TextView(this).apply{text=s;textSize=z;setTextColor(Color.WHITE);setPadding(0,14,0,8)}
        root.addView(title("ClinicalWatch Companion",28f));root.addView(title("Samsung Health",20f));status=title("Install both apps, then grant Samsung Health access.",15f);root.addView(status)
        root.addView(title("For this test build you can enter today's Samsung Health steps below. The watch sync path, battery and appearance are live; automatic Health reading is the next SDK-specific layer.",13f))
        steps=EditText(this).apply{hint="Today's steps (test)";inputType=2;setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);root.addView(this)}
        root.addView(title("Watch appearance",20f));name=EditText(this).apply{hint="Displayed name";setText("DAN");setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);root.addView(this)}
        val colours=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};listOf("#BEEB00","#00E5FF","#FF3B30","#FF9500","#FFFFFF").forEach{hex->colours.addView(Button(this).apply{text=" ";setBackgroundColor(Color.parseColor(hex));setOnClickListener{accent=hex}},LinearLayout.LayoutParams(0,90,1f))};root.addView(colours)
        preview=ImageView(this).apply{adjustViewBounds=true;minimumHeight=180;root.addView(this)};Button(this).apply{text="Choose crest / shield PNG";setOnClickListener{pickCrest()};root.addView(this)}
        Button(this).apply{text="SYNC EVERYTHING TO WATCH";setOnClickListener{syncAll()};root.addView(this)};setContentView(ScrollView(this).apply{addView(root)})
    }
    private fun pickCrest(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/png"},10)}
    override fun onActivityResult(r:Int,result:Int,data:Intent?){super.onActivityResult(r,result,data);if(r==10&&result==RESULT_OK){crestUri=data?.data;crestUri?.let{try{contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Throwable){};preview.setImageURI(it)}}}
    private fun phoneBattery():Int{val i=registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED));val l=i?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,-1)?:-1;val s=i?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE,-1)?:-1;return if(l>=0&&s>0)l*100/s else -1}
    private fun syncAll(){val stepValue=steps.text.toString().toLongOrNull()?.coerceAtLeast(0)?:0;val payload="name=${name.text.toString().trim().ifEmpty{"DAN"}}\naccent=$accent\nsteps=$stepValue\nphoneBattery=${phoneBattery()}".toByteArray();Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener{nodes->nodes.forEach{Wearable.getMessageClient(this).sendMessage(it.id,"/clinicalwatch/config",payload)};if(nodes.isEmpty())status.text="No connected watch found" else status.text="Synced: $stepValue steps • phone ${phoneBattery()}%"};crestUri?.let{u->try{val raw=contentResolver.openInputStream(u)?.use{it.readBytes()}?:return@let;val bmp=BitmapFactory.decodeByteArray(raw,0,raw.size);val max=256;val scale=minOf(1f,max.toFloat()/maxOf(bmp.width,bmp.height));val resized=Bitmap.createScaledBitmap(bmp,(bmp.width*scale).toInt().coerceAtLeast(1),(bmp.height*scale).toInt().coerceAtLeast(1),true);val out=java.io.ByteArrayOutputStream();resized.compress(Bitmap.CompressFormat.PNG,90,out);Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener{ns->ns.forEach{Wearable.getMessageClient(this).sendMessage(it.id,"/clinicalwatch/crest",out.toByteArray())}}}catch(t:Throwable){status.text="Crest error: ${t.message}"}}}
}

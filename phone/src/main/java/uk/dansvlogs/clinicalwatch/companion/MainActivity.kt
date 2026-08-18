package uk.dansvlogs.clinicalwatch.companion

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import com.google.android.gms.wearable.Wearable

class MainActivity : Activity() {
    private lateinit var name: EditText
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private var accent = "#BEEB00"
    private var crestUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(40,40,40,40); setBackgroundColor(Color.rgb(16,19,16)) }
        fun title(s:String,size:Float=18f)=TextView(this).apply { text=s; textSize=size; setTextColor(Color.WHITE); setPadding(0,14,0,8) }
        root.addView(title("ClinicalWatch Companion",28f))
        root.addView(title("Samsung Health",20f))
        status=title("Health connection setup ready",15f); root.addView(status)
        Button(this).apply { text="Samsung Health permissions"; setOnClickListener { openHealthSettings() }; root.addView(this) }
        root.addView(title("Watch appearance",20f))
        name=EditText(this).apply { hint="Displayed name"; setText("DAN"); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); root.addView(this) }
        val colours=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        listOf("#BEEB00","#00E5FF","#FF3B30","#FF9500","#FFFFFF").forEach { hex -> colours.addView(Button(this).apply { text=" "; setBackgroundColor(Color.parseColor(hex)); setOnClickListener { accent=hex; Toast.makeText(this@MainActivity,"Accent $hex",Toast.LENGTH_SHORT).show() } },LinearLayout.LayoutParams(0,90,1f)) }
        root.addView(colours)
        preview=ImageView(this).apply { adjustViewBounds=true; minimumHeight=180; setPadding(0,12,0,12); root.addView(this) }
        Button(this).apply { text="Choose crest / shield PNG"; setOnClickListener { pickCrest() }; root.addView(this) }
        Button(this).apply { text="SYNC APPEARANCE TO WATCH"; setOnClickListener { syncAppearance() }; root.addView(this) }
        root.addView(title("The companion will also send Samsung Health steps and phone battery to the watch once Health access is connected.",14f))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun pickCrest(){ startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="image/png" },10) }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){ super.onActivityResult(requestCode,resultCode,data); if(requestCode==10&&resultCode==RESULT_OK){ crestUri=data?.data; crestUri?.let { contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION); preview.setImageURI(it) } } }
    private fun openHealthSettings(){ try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))) } catch(_:Throwable){}; status.text="Grant Samsung Health access when prompted by the Health SDK" }
    private fun syncAppearance(){
        val payload="name=${name.text.toString().trim().ifEmpty{"DAN"}}\naccent=$accent".toByteArray()
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes -> nodes.forEach { Wearable.getMessageClient(this).sendMessage(it.id,"/clinicalwatch/config",payload) }; status.text=if(nodes.isEmpty())"No connected watch found" else "Appearance sent to watch" }
        crestUri?.let { uri -> try { val bytes=contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@let; Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes -> nodes.forEach { Wearable.getMessageClient(this).sendMessage(it.id,"/clinicalwatch/crest",bytes) } } } catch(_:Throwable){} }
    }
}

package uk.dansvlogs.clinicalwatch

import android.content.Context
import android.util.Base64
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WatchDataService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        val prefs = getSharedPreferences("companion", Context.MODE_PRIVATE)
        when (event.path) {
            "/clinicalwatch/config" -> {
                val lines = event.data.toString(Charsets.UTF_8).lines()
                val edit = prefs.edit()
                lines.forEach { line ->
                    val i = line.indexOf('=')
                    if (i > 0) edit.putString(line.substring(0, i), line.substring(i + 1))
                }
                edit.apply()
            }
            "/clinicalwatch/crest" -> prefs.edit().putString("crest", Base64.encodeToString(event.data, Base64.NO_WRAP)).apply()
        }
        sendBroadcast(android.content.Intent("uk.dansvlogs.clinicalwatch.DATA_CHANGED").setPackage(packageName))
    }
}

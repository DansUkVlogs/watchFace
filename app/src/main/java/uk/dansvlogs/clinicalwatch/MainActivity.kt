package uk.dansvlogs.clinicalwatch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {
    private lateinit var watchView: ClinicalWatchView
    private val permissionRequest = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        watchView = ClinicalWatchView(this)
        setContentView(watchView)
        AmbientModeSupport.attach(this)
        requestSensorPermissions()
    }

    private fun requestSensorPermissions() {
        val wanted = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            wanted += Manifest.permission.ACTIVITY_RECOGNITION
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            wanted += Manifest.permission.BODY_SENSORS
        }
        if (Build.VERSION.SDK_INT >= 36 && ContextCompat.checkSelfPermission(this, "android.permission.health.READ_HEART_RATE") != PackageManager.PERMISSION_GRANTED) {
            wanted += "android.permission.health.READ_HEART_RATE"
        }
        if (wanted.isEmpty()) watchView.startSensors() else ActivityCompat.requestPermissions(this, wanted.toTypedArray(), permissionRequest)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest) watchView.startSensors()
    }

    override fun onResume() {
        super.onResume()
        if (::watchView.isInitialized) watchView.startSensors()
    }

    override fun onPause() {
        if (::watchView.isInitialized) watchView.stopSensors()
        super.onPause()
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = object : AmbientModeSupport.AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle?) { watchView.setAmbient(true) }
        override fun onExitAmbient() { watchView.setAmbient(false) }
        override fun onUpdateAmbient() { watchView.invalidate() }
    }
}

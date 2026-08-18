package uk.dansvlogs.clinicalwatch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider, SensorEventListener {
    private lateinit var watchView: ClinicalWatchView
    private lateinit var sensorManager: SensorManager
    private var stepCounter: Sensor? = null
    private val permissionRequest = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        watchView = ClinicalWatchView(this)
        setContentView(watchView)
        AmbientModeSupport.attach(this)
        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val wanted = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) wanted += Manifest.permission.ACTIVITY_RECOGNITION
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) wanted += Manifest.permission.BODY_SENSORS
        if (Build.VERSION.SDK_INT >= 36 && ContextCompat.checkSelfPermission(this, "android.permission.health.READ_HEART_RATE") != PackageManager.PERMISSION_GRANTED) wanted += "android.permission.health.READ_HEART_RATE"
        if (wanted.isEmpty()) startSensors() else ActivityCompat.requestPermissions(this, wanted.toTypedArray(), permissionRequest)
    }

    private fun startSensors() {
        watchView.startSensors()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            stepCounter?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) watchView.setExternalSteps(event.values[0].toLong())
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest) startSensors()
    }
    override fun onResume() { super.onResume(); if (::watchView.isInitialized) startSensors() }
    override fun onDestroy() { if (::sensorManager.isInitialized) sensorManager.unregisterListener(this); super.onDestroy() }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = object : AmbientModeSupport.AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle?) { watchView.setAmbient(true) }
        override fun onExitAmbient() { watchView.setAmbient(false) }
        override fun onUpdateAmbient() { watchView.invalidate() }
    }
}

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
    private var activityStepCounter: Sensor? = null
    private var activityStepDetector: Sensor? = null
    private var detectorSteps = 0L
    private val permissionRequest = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        activityStepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        activityStepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        detectorSteps = getSharedPreferences("step_fallback", MODE_PRIVATE).getLong("steps", 0L)
        watchView = ClinicalWatchView(this)
        setContentView(watchView)
        AmbientModeSupport.attach(this)
        requestSensorPermissions()
    }

    private fun requestSensorPermissions() {
        val wanted = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) wanted += Manifest.permission.ACTIVITY_RECOGNITION
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) wanted += Manifest.permission.BODY_SENSORS
        if (Build.VERSION.SDK_INT >= 36 && ContextCompat.checkSelfPermission(this, "android.permission.health.READ_HEART_RATE") != PackageManager.PERMISSION_GRANTED) wanted += "android.permission.health.READ_HEART_RATE"
        if (wanted.isEmpty()) startAllSensors() else ActivityCompat.requestPermissions(this, wanted.toTypedArray(), permissionRequest)
    }

    private fun startAllSensors() {
        watchView.startSensors()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            activityStepCounter?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            activityStepDetector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                // Galaxy Watch reports this as cumulative steps since boot. Using the raw value avoids
                // the old bug where establishing a fresh baseline made the face permanently display 0.
                publishSteps(event.values[0].toLong().coerceAtLeast(0L))
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                detectorSteps += (event.values.firstOrNull()?.toLong() ?: 1L).coerceAtLeast(1L)
                getSharedPreferences("step_fallback", MODE_PRIVATE).edit().putLong("steps", detectorSteps).apply()
                // Used on firmware where STEP_COUNTER never emits to a sideloaded app.
                if (activityStepCounter == null) publishSteps(detectorSteps)
            }
        }
    }

    private fun publishSteps(value: Long) {
        try {
            val field = ClinicalWatchView::class.java.getDeclaredField("steps")
            field.isAccessible = true
            field.set(watchView, value)
            watchView.invalidate()
        } catch (_: Throwable) { }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest) startAllSensors()
    }

    override fun onResume() {
        super.onResume()
        if (::watchView.isInitialized) startAllSensors()
    }

    override fun onPause() {
        if (::watchView.isInitialized) watchView.stopSensors()
        super.onPause()
    }

    override fun onDestroy() {
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = object : AmbientModeSupport.AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle?) { watchView.setAmbient(true) }
        override fun onExitAmbient() { watchView.setAmbient(false) }
        override fun onUpdateAmbient() { watchView.invalidate() }
    }
}

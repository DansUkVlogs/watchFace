package uk.dansvlogs.clinicalwatch

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.wear.ambient.AmbientModeSupport

class MainActivity : Activity(), AmbientModeSupport.AmbientCallbackProvider {
    private lateinit var watchView: ClinicalWatchView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        watchView = ClinicalWatchView(this)
        setContentView(watchView)
        AmbientModeSupport.attach(this)
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback =
        object : AmbientModeSupport.AmbientCallback() {
            override fun onEnterAmbient(ambientDetails: Bundle?) {
                watchView.setAmbient(true)
            }

            override fun onExitAmbient() {
                watchView.setAmbient(false)
            }

            override fun onUpdateAmbient() {
                watchView.invalidate()
            }
        }
}

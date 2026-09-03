package dev.yonc.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import dev.yonc.sample.ui.SampleScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Notification permission (Android 13+): the SDK owns detection and the system dialog, the
        // host only lends its activity. Safe on every start — the dialog appears only while the
        // system still allows one; the settings screen never opens uninvited.
        (application as SampleApplication).yonc.backgroundWork.requestNotifications(this)
        setContent {
            MaterialTheme {
                SampleScreen()
            }
        }
    }
}

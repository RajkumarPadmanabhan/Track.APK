package com.trackme.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.trackme.app.ui.TrackMeApp
import com.trackme.app.ui.TrackTheme

class MainActivity : ComponentActivity() {

    private val vm: TrackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        runCatching {
            com.trackme.app.data.scheduleBackgroundSync(applicationContext)
        }

        val shared = runCatching {
            if (intent?.action == Intent.ACTION_SEND) {
                intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            } else null
        }.getOrNull()

        setContent {
            TrackTheme(accentColor = vm.selectedThemeColor.color) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TrackMeApp(vm, shared)
                }
            }
        }
        addCrashlyticsTestButton()
    }

    /** Debug-only control from the Crashlytics setup guide. Hidden on release builds. */
    private fun addCrashlyticsTestButton() {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) return
        val crashButton = Button(this)
        crashButton.text = "Test Crash"
        crashButton.setOnClickListener {
            throw RuntimeException("Test Crash")
        }
        addContentView(
            crashButton,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }
}

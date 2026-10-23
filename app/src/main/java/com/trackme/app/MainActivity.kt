package com.trackme.app

import android.content.Intent
import android.os.Bundle
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
    }
}

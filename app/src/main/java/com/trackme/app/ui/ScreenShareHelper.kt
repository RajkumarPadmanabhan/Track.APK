package com.trackme.app.ui

import android.content.Context
import android.content.Intent
import com.trackme.app.data.FirebaseWebSync
import com.trackme.app.data.StaticSyncManager
import com.trackme.app.data.WebSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

object ScreenShareHelper {
    // Live GitHub Pages link
    const val WEB_HOST_URL = "https://rajkumarpadmanabhan.github.io/Track.APK/"

    fun shareScreenToPc(
        context: Context,
        platforms: List<StaticSyncManager.PlatformExportItem>
    ) {
        // 1. Generate unique 6-character session code (e.g. TRK-5122)
        val randomNum = Random.nextInt(1000, 9999)
        val sessionCode = "TRK-$randomNum"
        // 2. Direct magic pairing link
        val directPairUrl = "$WEB_HOST_URL?pair=$sessionCode"
        // 3. Register session locally on device
        WebSyncManager.pairWithWeb(codeOrToken = sessionCode)
        // 4. Push active platform stats to Firebase Realtime Database
        CoroutineScope(Dispatchers.IO).launch {
            FirebaseWebSync.pairAndSync(
                sessionCode = sessionCode,
                platforms = platforms
            )
        }
        // 5. Open Android Share Sheet with the ready-to-click magic link
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Open TrackMe on PC")
            putExtra(
                Intent.EXTRA_TEXT,
                "Open your live TrackMe dashboard on PC:\n$directPairUrl\n\nOr visit $WEB_HOST_URL and enter pairing code: $sessionCode"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, "Share Screen to PC").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

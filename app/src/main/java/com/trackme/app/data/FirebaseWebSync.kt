package com.trackme.app.data

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object FirebaseWebSync {
    // Exact Realtime Database URL
    private const val FIREBASE_DATABASE_URL = "https://trackme-1c59a-default-rtdb.firebaseio.com"

    suspend fun pairAndSync(
        sessionCode: String,
        platforms: List<StaticSyncManager.PlatformExportItem>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Build the JSON payload to push to Firebase
            val platformsArray = JSONArray()
            platforms.forEach {
                platformsArray.put(JSONObject().apply {
                    put("platform", it.platform)
                    put("username", it.username)
                    put("metric", it.metric)
                    put("label", it.label)
                    put("profileUrl", it.profileUrl)
                })
            }
            val payload = JSONObject().apply {
                put("isPaired", true)
                put("sessionCode", sessionCode)
                put("deviceName", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
                put("timestamp", System.currentTimeMillis())
                put("platforms", platformsArray)
            }
            // HTTP PUT to Firebase REST endpoint
            val endpoint = "$FIREBASE_DATABASE_URL/sessions/$sessionCode.json"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 6000
                readTimeout = 6000
            }
            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

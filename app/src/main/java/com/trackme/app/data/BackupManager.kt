package com.trackme.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class BackupRecord(
    val platform: String,
    val username: String,
    val profileUrl: String,
    val goalCount: Long? = null
)

object BackupManager {
    // 1. Export tracked URLs and settings to JSON
    fun exportBackupToJson(userEmail: String, items: List<BackupRecord>): String {
        val root = JSONObject().apply {
            put("app", "TrackMe")
            put("email", userEmail)
            put("version", 1)
            put("timestamp", System.currentTimeMillis())
            val array = JSONArray()
            items.forEach { item ->
                val obj = JSONObject().apply {
                    put("platform", item.platform)
                    put("username", item.username)
                    put("profileUrl", item.profileUrl)
                    if (item.goalCount != null) put("goalCount", item.goalCount)
                }
                array.put(obj)
            }
            put("trackedItems", array)
        }
        return root.toString(2)
    }

    // 2. Write JSON string to file picked by user
    fun writeToUri(context: Context, uri: Uri, jsonContent: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                OutputStreamWriter(stream).use { writer ->
                    writer.write(jsonContent)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 3. Read & parse backup JSON file when restoring
    fun parseBackupFile(context: Context, uri: Uri): List<BackupRecord>? {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            } ?: return null
            val root = JSONObject(content)
            val array = root.optJSONArray("trackedItems") ?: return emptyList()
            val result = mutableListOf<BackupRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    BackupRecord(
                        platform = obj.getString("platform"),
                        username = obj.optString("username", obj.optString("handle")),
                        profileUrl = obj.getString("profileUrl"),
                        goalCount = if (obj.has("goalCount") && !obj.isNull("goalCount")) obj.getLong("goalCount") else null
                    )
                )
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

package com.trackme.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiException(message: String) : Exception(message)

object Http {
    fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "TrackMe-Android")
        try {
            val code = conn.responseCode
            if (code == 404) throw ApiException("Profile not found.")
            if (code == 403 || code == 429) {
                throw ApiException("Rate limit reached. Please try again in a few minutes.")
            }
            if (code !in 200..299) throw ApiException("The server returned error $code.")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

object HttpHtml {
    /** Plain GET for public web pages/JSON endpoints (no API headers). Returns null on any failure. */
    fun get(url: String, accept: String = "text/html"): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 25000
        conn.setRequestProperty("Accept", accept)
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) TrackMe")
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}

/** Returns the string value, or null when the key is missing, null or blank. */
internal fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

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

/** Prose from a profile README. Code blocks are left out, the same way Git hides them from the reading view. */
internal fun cleanOverview(raw: String): String? {
    val stripped = raw
        .replace(Regex("<!--[\\s\\S]*?-->"), " ")
        .replace(Regex("```[\\s\\S]*?```"), "\n")
        .replace(Regex("(?is)<pre\\b[^>]*>.*?</pre>"), "\n")
        .replace(Regex("(?is)<code\\b[^>]*>.*?</code>"), "\n")
        .replace(Regex("(?m)^( {4,}|\\t)\\S.*$"), "")
        .replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), " ")
        .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
        .replace(Regex("`([^`\\n]+)`"), "$1")
        .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("[*_>]"), "")
    val text = stripped.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !overviewLineIsCode(it) }
        .joinToString("\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
    if (text.isBlank()) return null
    return text.take(1600)
}

private fun overviewLineIsCode(line: String): Boolean {
    val t = line.trim()
    if (t.startsWith("import ") || t.startsWith("package ") || t.startsWith("fun ") ||
        t.startsWith("class ") || t.startsWith("def ") || t.startsWith("const ") ||
        t.startsWith("let ") || t.startsWith("var ") || t.startsWith("public ") ||
        t.startsWith("private ") || t.startsWith("protected ") || t.startsWith("#include") ||
        t.startsWith("using ") || (t.startsWith("from ") && " import " in t)
    ) return true
    if ((t.startsWith("<") && t.endsWith(">")) || t == "{" || t == "}" || t.endsWith("{") || t.endsWith(";")) return true
    return t.length > 12 && t.count { it in "{};=<>" } >= 3
}

/** Returns the string value, or null when the key is missing, null or blank. */
internal fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

object WeatherApi {
    suspend fun checkIsRaining(): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val ipJson = Http.get("https://ipapi.co/json/")
            val obj = JSONObject(ipJson)
            val lat = obj.optDouble("latitude", 0.0)
            val lon = obj.optDouble("longitude", 0.0)
            if (lat == 0.0 && lon == 0.0) return@withContext false
            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
            val weatherJson = Http.get(weatherUrl)
            val wObj = JSONObject(weatherJson).optJSONObject("current_weather") ?: return@withContext false
            val code = wObj.optInt("weathercode", -1)
            val rainCodes = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
            code in rainCodes
        } catch (_: Exception) {
            false
        }
    }
}

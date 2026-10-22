package com.trackme.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object InstagramScraper {
    suspend fun fetchCountsOnly(rawUrl: String): InstagramProfileStats? = withContext(Dispatchers.IO) {
        try {
            // Normalize username from URL (e.g. https://instagram.com/username/)
            val cleanUrl = rawUrl.trim().removeSuffix("/")
            val username = cleanUrl.substringAfterLast("/")
            val targetUrl = "https://www.instagram.com/$username/"
            val connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                // Using crawler user agent to get plain OpenGraph HTML directly
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
                )
                connectTimeout = 8000
                readTimeout = 8000
            }
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            // Extract pattern: "XX Followers, YY Following, ZZ Posts"
            val pattern = Pattern.compile("content=\"([0-9.,KkMm]+)\\s+Followers,\\s+([0-9.,KkMm]+)\\s+Following,\\s+([0-9.,KkMm]+)\\s+Posts")
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val followers = matcher.group(1) ?: "0"
                val following = matcher.group(2) ?: "0"
                val posts = matcher.group(3) ?: "0"
                InstagramProfileStats(
                    username = username,
                    profileUrl = targetUrl,
                    followers = followers,
                    following = following,
                    posts = posts,
                    lastUpdated = System.currentTimeMillis()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

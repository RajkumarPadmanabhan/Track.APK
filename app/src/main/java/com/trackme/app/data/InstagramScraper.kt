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
            val html = connection.inputStream.bufferedReader().use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (sb.length < 600_000) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }
            val counts = readCounts(html) ?: return@withContext null
            InstagramProfileStats(
                username = username,
                profileUrl = targetUrl,
                followers = counts.first,
                following = counts.second,
                lastUpdated = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            // connection may fail before it is assigned; close only when open
        }
    }

    /** Followers and following only. Posts, name, and photo are ignored. */
    private fun readCounts(html: String): Pair<String, String>? {
        val meta = Pattern.compile(
            """([\d.,]+[KkMm]?)\s+Followers,\s+([\d.,]+[KkMm]?)\s+Following""",
            Pattern.CASE_INSENSITIVE,
        ).matcher(html)
        if (meta.find()) {
            val followers = meta.group(1) ?: return null
            val following = meta.group(2) ?: return null
            return followers to following
        }
        val followers = Pattern.compile(""""edge_followed_by"\s*:\s*\{\s*"count"\s*:\s*(\d+)""")
            .matcher(html)
        val following = Pattern.compile(""""edge_follow"\s*:\s*\{\s*"count"\s*:\s*(\d+)""")
            .matcher(html)
        if (followers.find() && following.find()) {
            return followers.group(1) to following.group(1)
        }
        return null
    }
}

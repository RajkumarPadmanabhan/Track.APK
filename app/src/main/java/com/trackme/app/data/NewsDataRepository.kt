package com.trackme.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object NewsDataRepository {
    // NewsData.io API Key
    private const val API_KEY = "pub_72931aef70e44b829ed6d3d3b764ef1a01784"

    /** Latest headlines, following every next page the API returns. */
    suspend fun fetchLiveNews(): List<UserNewsPost> = withContext(Dispatchers.IO) {
        val news = LinkedHashMap<String, UserNewsPost>()
        var pageToken: String? = null
        repeat(10) { page ->
            val pageQuery = pageToken?.let { "&page=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
            val latest = "https://newsdata.io/api/1/latest?apikey=$API_KEY&language=en$pageQuery"
            val fallback = "https://newsdata.io/api/1/news?apikey=$API_KEY&language=en&q=${URLEncoder.encode("news", "UTF-8")}$pageQuery"
            val json = getJson(latest) ?: getJson(fallback) ?: return@withContext news.values.toList()
            val results = json.optJSONArray("results")
            if (results == null || results.length() == 0) return@withContext news.values.toList()
            val before = news.size
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val title = item.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: continue
                val link = item.optString("link")
                val id = item.optString("article_id").takeIf { it.isNotBlank() && it != "null" }
                    ?: link.takeIf { it.isNotBlank() && it != "null" }
                    ?: "$page-$i-$title"
                val description = item.optString("description").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
                val sourceId = item.optString("source_id", "News").ifBlank { "News" }
                val imageUrl = item.optString("image_url").takeIf { it.isNotBlank() && it != "null" }
                val category = item.optJSONArray("category")?.optString(0)
                    ?.takeIf { it.isNotBlank() && it != "null" }
                    ?.replaceFirstChar { it.uppercase() }
                    ?: "News"
                news[id] = UserNewsPost(
                    id = id,
                    title = title,
                    description = description,
                    sourceName = sourceId.replaceFirstChar { it.uppercase() },
                    sourceUrl = link,
                    publishedAt = item.optString("pubDate", "Recently").take(10),
                    imageUrl = imageUrl,
                    categoryTag = category,
                )
            }
            val next = json.optString("nextPage").takeIf { it.isNotBlank() && it != "null" && it != pageToken }
            if (next == null || news.size == before) return@withContext news.values.toList()
            pageToken = next
        }
        news.values.toList()
    }

    private fun getJson(endpoint: String): JSONObject? {
        return try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (connection.responseCode != 200) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (json.optString("status").equals("error", true)) return null
            json
        } catch (_: Exception) {
            null
        }
    }
}

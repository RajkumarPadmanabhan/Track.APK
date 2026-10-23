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

    private val feeds = listOf(
        "https://feeds.bbci.co.uk/news/rss.xml",
        "https://feeds.bbci.co.uk/news/world/rss.xml",
        "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml",
        "https://www.theverge.com/rss/index.xml",
        "https://www.aljazeera.com/xml/rss/all.xml",
        "https://www.hindustantimes.com/feeds/rss/india-news/rssfeed.xml",
        "https://timesofindia.indiatimes.com/rssfeedstopstories.cms",
        "https://www.thehindu.com/news/national/feeder/default.rss",
        "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en",
    )

    /** One batch per source, so the screen can show posts as soon as each feed arrives. */
    suspend fun liveBatches(): List<List<UserNewsPost>> = withContext(Dispatchers.IO) {
        val batches = ArrayList<List<UserNewsPost>>()
        val api = LinkedHashMap<String, UserNewsPost>()
        addNewsData(api)
        if (api.isNotEmpty()) batches += api.values.toList()
        for (feed in feeds) {
            val posts = readFeed(feed)
            if (posts.isNotEmpty()) batches += posts
        }
        batches
    }

    suspend fun fetchLiveNews(): List<UserNewsPost> {
        val news = LinkedHashMap<String, UserNewsPost>()
        for (batch in liveBatches()) {
            for (post in batch) {
                val key = post.sourceUrl.ifBlank { post.id }.lowercase()
                if (key.isNotBlank()) news.putIfAbsent(key, post)
            }
        }
        return news.values.toList()
    }

    private fun addNewsData(news: LinkedHashMap<String, UserNewsPost>) {
        var pageToken: String? = null
        repeat(8) { page ->
            val pageQuery = pageToken?.let { "&page=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
            val latest = "https://newsdata.io/api/1/latest?apikey=$API_KEY&language=en$pageQuery"
            val fallback = "https://newsdata.io/api/1/news?apikey=$API_KEY&language=en&q=${URLEncoder.encode("news", "UTF-8")}$pageQuery"
            val json = getJson(latest) ?: getJson(fallback) ?: return
            val results = json.optJSONArray("results")
            if (results == null || results.length() == 0) return
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
                news.putIfAbsent(link.ifBlank { id }.lowercase(), UserNewsPost(
                    id = id,
                    title = title,
                    description = description,
                    sourceName = sourceId.replaceFirstChar { it.uppercase() },
                    sourceUrl = link,
                    publishedAt = item.optString("pubDate", "Recently").take(10),
                    imageUrl = imageUrl,
                    categoryTag = category,
                ))
            }
            val next = json.optString("nextPage").takeIf { it.isNotBlank() && it != "null" && it != pageToken }
            if (next == null || news.size == before) return
            pageToken = next
        }
    }

    private fun readFeed(endpoint: String): List<UserNewsPost> {
        val xml = getText(endpoint) ?: return emptyList()
        val chunks = Regex("(?is)<(?:item|entry)\\b[^>]*>(.*?)</(?:item|entry)>").findAll(xml)
        val posts = ArrayList<UserNewsPost>()
        val source = sourceName(endpoint)
        for (chunk in chunks) {
            if (posts.size >= 80) break
            val body = chunk.groupValues[1]
            val title = tagText(body, "title") ?: continue
            val link = tagText(body, "link")
                ?: Regex("""(?i)<link[^>]+href=["']([^"']+)["']""").find(body)?.groupValues?.get(1)
                ?: continue
            val description = plainText(tagText(body, "description") ?: tagText(body, "summary") ?: tagText(body, "content") ?: "")
            val image = Regex("""(?i)(?:thumbnail|content|enclosure)[^>]*\surl=["']([^"']+)["']""")
                .find(body)?.groupValues?.get(1)
                ?: imageFromHtml(body)
            posts += UserNewsPost(
                id = link,
                title = plainText(title),
                description = description,
                sourceName = source,
                sourceUrl = link,
                publishedAt = (tagText(body, "pubDate") ?: tagText(body, "published") ?: tagText(body, "updated") ?: "Today").take(16),
                imageUrl = image?.takeIf { it.startsWith("http") },
                categoryTag = "Live",
            )
        }
        return posts
    }

    private fun tagText(body: String, tag: String): String? {
        val match = Regex("(?is)<$tag\\b[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</$tag>").find(body) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotBlank() }
    }

    private fun getText(endpoint: String): String? {
        return try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0")
                setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private fun sourceName(endpoint: String): String {
        val host = try {
            URL(endpoint).host.removePrefix("www.").removePrefix("feeds.").removePrefix("rss.")
        } catch (_: Exception) {
            "News"
        }
        return host.substringBefore('.').replaceFirstChar { it.uppercase() }
    }

    private fun plainText(raw: String): String {
        return raw.replace(Regex("<[^>]{0,300}>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun imageFromHtml(raw: String): String? {
        val match = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(raw)
        return match?.groupValues?.get(1)?.takeIf { it.startsWith("http") }
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

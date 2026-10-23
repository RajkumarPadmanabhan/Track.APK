package com.trackme.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class NewsTopic(val id: String, val label: String, val feeds: List<String>)

object NewsTopics {
    const val MAX = 5
    private fun google(query: String): String {
        val q = URLEncoder.encode(query, "UTF-8")
        return "https://news.google.com/rss/search?q=$q&hl=en-IN&gl=IN&ceid=IN:en"
    }
    val all = listOf(
        NewsTopic("sports", "Sports", listOf(google("sports"), "https://feeds.bbci.co.uk/sport/rss.xml")),
        NewsTopic("finance", "Finance", listOf(google("finance markets"), "https://feeds.bbci.co.uk/news/business/rss.xml")),
        NewsTopic("cyber", "Cyber security", listOf(google("cybersecurity"), "https://feeds.feedburner.com/TheHackersNews")),
        NewsTopic("art", "Art", listOf(google("art exhibition painting"))),
        NewsTopic("racing", "Racing", listOf(google("Formula 1 motorsport racing"))),
        NewsTopic("science", "Science", listOf(google("science"), "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml")),
        NewsTopic("technology", "Technology", listOf(google("technology"), "https://www.theverge.com/rss/index.xml")),
        NewsTopic("health", "Health", listOf(google("health"), "https://feeds.bbci.co.uk/news/health/rss.xml")),
        NewsTopic("world", "World", listOf(google("world news"), "https://feeds.bbci.co.uk/news/world/rss.xml", "https://www.theguardian.com/world/rss")),
        NewsTopic("business", "Business", listOf(google("business"))),
        NewsTopic("entertainment", "Entertainment", listOf(google("entertainment movies music"))),
        NewsTopic("climate", "Climate", listOf(google("climate change environment"))),
        NewsTopic("food", "Food", listOf(google("food cooking"))),
        NewsTopic("education", "Education", listOf(google("education schools"))),
        NewsTopic("politics", "Politics", listOf(google("politics"))),
        NewsTopic("travel", "Travel", listOf(google("travel"))),
    )
    fun selected(ids: List<String>): List<NewsTopic> = ids.mapNotNull { id -> all.firstOrNull { it.id == id } }.take(MAX)
}

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
        "https://feeds.feedburner.com/ndtvnews-top-stories",
        "https://www.indiatoday.in/rss/home",
        "https://www.theguardian.com/world/rss",
        "https://rss.cnn.com/rss/edition.rss",
        "https://feeds.npr.org/1001/rss.xml",
        "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en",
    )

    /** Headlines for the topics the user picked. Each feed is sent as it arrives. */
    fun liveFlow(topics: List<NewsTopic>): Flow<List<UserNewsPost>> = channelFlow {
        for (topic in topics) {
            for (feed in topic.feeds) {
                launch(Dispatchers.IO) {
                    val posts = readFeedCached(feed, topic.label)
                    if (posts.isNotEmpty()) send(posts)
                }
            }
        }
    }

    suspend fun fetchLiveNews(topics: List<NewsTopic>): List<UserNewsPost> {
        val news = LinkedHashMap<String, UserNewsPost>()
        liveFlow(topics).collect { batch ->
            for (post in batch) {
                val key = post.sourceUrl.ifBlank { post.id }.lowercase()
                if (key.isNotBlank()) news.putIfAbsent(key, post)
            }
        }
        return news.values.toList()
    }

    private fun addNewsData(news: LinkedHashMap<String, UserNewsPost>) {
        var pageToken: String? = null
        repeat(2) { page ->
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

    private fun readFeed(endpoint: String, category: String): List<UserNewsPost> {
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
            val image = imageUrl(body)
            posts += UserNewsPost(
                id = link,
                title = plainText(title),
                description = description,
                sourceName = source,
                sourceUrl = link,
                publishedAt = tagText(body, "pubDate") ?: tagText(body, "published") ?: tagText(body, "updated") ?: "Today",
                imageUrl = image?.takeIf { it.startsWith("http") },
                categoryTag = category,
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

    private fun imageUrl(body: String): String? {
        val patterns = listOf(
            """(?i)<(?:media:thumbnail|media:content|media:group|enclosure|image)\b[^>]*\b(?:url|href)=["']([^"']+)["']""",
            """(?i)<img[^>]+src=["']([^"']+)["']""",
        )
        for (pattern in patterns) {
            val raw = Regex(pattern).find(body)?.groupValues?.get(1) ?: continue
            val url = raw.replace("&amp;", "&").trim()
            if (url.startsWith("http")) return url
        }
        return null
    }

    private val feedCache = HashMap<String, Pair<Long, List<UserNewsPost>>>()

    private fun readFeedCached(endpoint: String, category: String): List<UserNewsPost> {
        val now = System.currentTimeMillis()
        synchronized(feedCache) {
            feedCache[endpoint]?.let { (at, posts) ->
                if (now - at < 8_000L && posts.isNotEmpty()) return posts
            }
        }
        val posts = readFeed(endpoint, category)
        if (posts.isNotEmpty()) synchronized(feedCache) { feedCache[endpoint] = now to posts }
        return posts
    }

    fun cleanPost(post: UserNewsPost): UserNewsPost {
        val title = stripLinks(post.title)
        val description = stripLinks(post.description)
        return if (title == post.title && description == post.description) post
        else post.copy(title = title, description = description)
    }

    /** Newest day first. Stories from the chosen topics stay in date order. */
    fun arrange(posts: List<UserNewsPost>): List<UserNewsPost> {
        if (posts.size < 2) return posts
        return posts.sortedWith(
            compareByDescending<UserNewsPost> {
                val time = parseNewsTime(it.publishedAt)
                if (time > 0L) time else Long.MIN_VALUE
            }.thenBy { it.title }
        )
    }

    fun shortNewsDate(raw: String): String {
        val time = parseNewsTime(raw)
        if (time <= 0L) return stripLinks(raw).take(18)
        val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.US)
        return fmt.format(java.util.Date(time))
    }

    private fun stripLinks(raw: String): String {
        return raw.replace(Regex("""(?i)https?://\S+|www\.\S+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('-', '–', '—', '|', '·', ' ')
            .trim()
    }

    private fun parseNewsTime(raw: String): Long {
        val text = raw.trim()
        if (text.isBlank() || text.equals("Today", true) || text.equals("Recently", true)) return 0L
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, d MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
        )
        for (pattern in patterns) {
            try {
                val format = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                format.isLenient = false
                if (pattern.endsWith("'Z'")) format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val date = format.parse(text) ?: continue
                return date.time
            } catch (_: Exception) {
            }
        }
        return 0L
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

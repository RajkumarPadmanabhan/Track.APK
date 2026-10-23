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

    suspend fun fetchNewsForPlatforms(activePlatforms: List<Platform>): List<UserNewsPost> = withContext(Dispatchers.IO) {
        val newsList = mutableListOf<UserNewsPost>()
        val queryKeywords = mutableListOf<String>()
        if (activePlatforms.contains(Platform.YOUTUBE)) queryKeywords.add("youtube")
        if (activePlatforms.contains(Platform.INSTAGRAM)) queryKeywords.add("instagram")
        if (activePlatforms.contains(Platform.LINKEDIN)) queryKeywords.add("linkedin")
        if (activePlatforms.any { it == Platform.GITHUB || it == Platform.GITLAB }) queryKeywords.add("github")
        if (activePlatforms.contains(Platform.AMAZON)) queryKeywords.add("amazon")
        if (activePlatforms.contains(Platform.FLIPKART)) queryKeywords.add("flipkart")

        val searchQuery = if (queryKeywords.isNotEmpty()) {
            queryKeywords.joinToString(" OR ")
        } else {
            "technology OR social media"
        }

        try {
            val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
            val endpoint = "https://newsdata.io/api/1/news?apikey=$API_KEY&q=$encodedQuery&language=en"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (connection.responseCode != 200) {
                return@withContext emptyList()
            }
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(responseText)
            val resultsArray = jsonObject.optJSONArray("results") ?: return@withContext emptyList()
            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.getJSONObject(i)
                val articleId = item.optString("article_id", i.toString())
                val title = item.optString("title", "No Title")
                val description = item.optString("description", "")
                val link = item.optString("link", "")
                val sourceId = item.optString("source_id", "News")
                val pubDate = item.optString("pubDate", "Recently")
                val imageUrl = if (item.has("image_url") && !item.isNull("image_url")) item.optString("image_url") else null

                val categoryTag = when {
                    title.contains("YouTube", ignoreCase = true) || description.contains("YouTube", ignoreCase = true) -> "YouTube"
                    title.contains("Instagram", ignoreCase = true) || description.contains("Instagram", ignoreCase = true) -> "Instagram"
                    title.contains("LinkedIn", ignoreCase = true) || description.contains("LinkedIn", ignoreCase = true) -> "LinkedIn"
                    title.contains("GitHub", ignoreCase = true) || description.contains("Git", ignoreCase = true) -> "Dev"
                    else -> "Trending"
                }
                newsList.add(
                    UserNewsPost(
                        id = articleId,
                        title = title,
                        description = description,
                        sourceName = sourceId.replaceFirstChar { it.uppercase() },
                        sourceUrl = link,
                        publishedAt = pubDate.take(10),
                        imageUrl = if (imageUrl == "null") null else imageUrl,
                        categoryTag = categoryTag
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        newsList
    }
}

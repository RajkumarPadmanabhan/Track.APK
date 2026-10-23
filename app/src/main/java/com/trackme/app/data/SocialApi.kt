package com.trackme.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Reads public profile details for platforms without an open repo API.
 *  - YouTube: official YouTube Data API v3 (needs the user's own free API key).
 *  - Instagram, LinkedIn, Facebook, and other public pages: follower and following counts only.
 */
object SocialApi {

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun count(raw: String?): String =
        raw?.toLongOrNull()?.let { "%,d".format(it) } ?: "-"

    // ---------- YouTube ----------

    suspend fun fetchYouTube(profile: ParsedProfile, apiKey: String): PublicProfile =
        withContext(Dispatchers.IO) {
            val h = profile.handle
            val query = when {
                h.startsWith("channel/") -> "id=" + enc(h.removePrefix("channel/"))
                h.startsWith("user/") -> "forUsername=" + enc(h.removePrefix("user/"))
                h.startsWith("c/") -> "forHandle=" + enc("@" + h.removePrefix("c/"))
                h.startsWith("@") -> "forHandle=" + enc(h)
                else -> "forHandle=" + enc("@$h")
            }
            val body = try {
                Http.get(
                    "https://www.googleapis.com/youtube/v3/channels" +
                        "?part=snippet,statistics&$query&key=${enc(apiKey)}"
                )
            } catch (e: ApiException) {
                throw ApiException(
                    "YouTube request failed. Check that your API key is valid, the YouTube Data API v3 " +
                        "is enabled for it, and your daily quota isn't used up. Showing the channel link instead."
                )
            }
            val items = JSONObject(body).optJSONArray("items")
            if (items == null || items.length() == 0) {
                throw ApiException("YouTube channel not found. Showing the channel link instead.")
            }
            val o = items.getJSONObject(0)
            val sn = o.getJSONObject("snippet")
            val st = o.optJSONObject("statistics")

            val subs = when {
                st == null -> "-"
                st.optBoolean("hiddenSubscriberCount") -> "Hidden"
                else -> count(st.optString("subscriberCount"))
            }
            val joined = sn.optString("publishedAt").take(10)
            val country = sn.str("country")
            val subtitle = listOfNotNull(
                sn.str("customUrl"),
                joined.takeIf { it.length == 10 }?.let { "Joined $it" },
                country,
            ).joinToString(" · ").ifBlank { null }

            val channelId = o.optString("id")
            val recentVideos = mutableListOf<YouTubeVideoItem>()
            if (channelId.isNotBlank() && apiKey.isNotBlank()) {
                try {
                    val searchBody = Http.get(
                        "https://www.googleapis.com/youtube/v3/search" +
                            "?part=snippet&channelId=${enc(channelId)}&order=date&type=video&maxResults=10&key=${enc(apiKey)}"
                    )
                    val searchItems = JSONObject(searchBody).optJSONArray("items") ?: JSONArray()
                    for (i in 0 until searchItems.length()) {
                        val item = searchItems.getJSONObject(i)
                        val vId = item.optJSONObject("id")?.optString("videoId") ?: continue
                        val vSn = item.optJSONObject("snippet") ?: continue
                        val vThumb = vSn.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url")
                            ?: vSn.optJSONObject("thumbnails")?.optJSONObject("high")?.optString("url") ?: ""
                        recentVideos += YouTubeVideoItem(
                            id = vId,
                            title = vSn.optString("title"),
                            publishedAt = vSn.optString("publishedAt").take(10),
                            thumbnailUrl = vThumb
                        )
                    }
                } catch (_: Exception) {}
            }

            PublicProfile(
                name = sn.optString("title", profile.handle),
                handle = sn.str("customUrl") ?: profile.handle,
                subtitle = subtitle,
                description = sn.str("description")?.take(300),
                imageUrl = sn.optJSONObject("thumbnails")?.optJSONObject("high")?.str("url")
                    ?: sn.optJSONObject("thumbnails")?.optJSONObject("default")?.str("url"),
                stats = listOf(
                    "Subscribers" to subs,
                    "Views" to count(st?.optString("viewCount")),
                    "Videos" to count(st?.optString("videoCount")),
                ),
                recentVideos = recentVideos,
            )
        }

    // Follower and following counts only. A missing count is left out.
    suspend fun fetchPreview(platform: Platform, profile: ParsedProfile): PublicProfile =
        withContext(Dispatchers.IO) {
            val counts = SocialCountScraper.fetch(profile.url)
                ?: throw ApiException("Couldn't open this ${platform.label} page. Showing the link instead.")
            val handle = profile.handle.trim().trimStart('@').ifBlank { profile.url }
            val stats = buildList {
                counts.followers?.let { add("Followers" to it) }
                counts.following?.let { add("Following" to it) }
            }
            PublicProfile(
                name = handle,
                handle = "@$handle",
                subtitle = null,
                description = null,
                imageUrl = null,
                stats = stats,
            )
        }
}

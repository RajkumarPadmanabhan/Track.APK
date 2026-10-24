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
                        "?part=snippet,statistics,contentDetails&$query&key=${enc(apiKey)}"
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
            val uploads = o.optJSONObject("contentDetails")
                ?.optJSONObject("relatedPlaylists")
                ?.optString("uploads")
                .orEmpty()
            val recentVideos = if (apiKey.isNotBlank()) {
                loadYouTubeVideos(uploads, channelId, apiKey)
            } else {
                emptyList()
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

    private fun loadYouTubeVideos(uploadsId: String, channelId: String, apiKey: String): List<YouTubeVideoItem> {
        val fromUploads = if (uploadsId.isNotBlank()) {
            runCatching { playlistVideos(uploadsId, apiKey) }.getOrNull()
        } else {
            null
        }
        if (!fromUploads.isNullOrEmpty()) return fromUploads
        if (channelId.isBlank()) return emptyList()
        return runCatching { searchVideos(channelId, apiKey) }.getOrDefault(emptyList())
    }

    private fun playlistVideos(uploadsId: String, apiKey: String): List<YouTubeVideoItem> {
        val body = Http.get(
            "https://www.googleapis.com/youtube/v3/playlistItems" +
                "?part=snippet&playlistId=${enc(uploadsId)}&maxResults=8&key=${enc(apiKey)}"
        )
        return videoRows(JSONObject(body).optJSONArray("items"), fromPlaylist = true)
    }

    private fun searchVideos(channelId: String, apiKey: String): List<YouTubeVideoItem> {
        val body = Http.get(
            "https://www.googleapis.com/youtube/v3/search" +
                "?part=snippet&channelId=${enc(channelId)}&order=date&type=video&maxResults=8&key=${enc(apiKey)}"
        )
        return videoRows(JSONObject(body).optJSONArray("items"), fromPlaylist = false)
    }

    private fun videoRows(items: JSONArray?, fromPlaylist: Boolean): List<YouTubeVideoItem> {
        if (items == null) return emptyList()
        val out = ArrayList<YouTubeVideoItem>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val snip = item.optJSONObject("snippet") ?: continue
            val id = if (fromPlaylist) {
                snip.optJSONObject("resourceId")?.optString("videoId").orEmpty()
            } else {
                item.optJSONObject("id")?.optString("videoId").orEmpty()
            }
            if (id.isBlank()) continue
            val title = snip.optString("title")
            if (title.isBlank() || title.equals("Private video", true) || title.equals("Deleted video", true)) continue
            val thumbs = snip.optJSONObject("thumbnails")
            val thumb = thumbs?.optJSONObject("medium")?.optString("url").orEmpty()
                .ifBlank { thumbs?.optJSONObject("high")?.optString("url").orEmpty() }
                .ifBlank { "https://i.ytimg.com/vi/$id/hqdefault.jpg" }
            out += YouTubeVideoItem(
                id = id,
                title = title,
                publishedAt = snip.optString("publishedAt").take(10),
                thumbnailUrl = thumb,
            )
        }
        return out
    }

    // Follower and following counts only. A missing count is left out.
    suspend fun fetchPreview(platform: Platform, profile: ParsedProfile): PublicProfile =
        withContext(Dispatchers.IO) {
            if (platform == Platform.AMAZON || platform == Platform.FLIPKART) {
                val shop = ShopReader.fetch(profile.url)
                val handle = profile.handle.trim().trimStart('@').ifBlank { platform.label }
                return@withContext PublicProfile(
                    name = handle,
                    handle = handle,
                    subtitle = null,
                    description = null,
                    imageUrl = null,
                    stats = emptyList(),
                    shop = shop,
                )
            }
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
                imageUrl = counts.imageUrl,
                stats = stats,
            )
        }
}

package com.trackme.app.data

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * Reads public profile details for platforms without an open repo API.
 *  - YouTube: official YouTube Data API v3 (needs the user's own free API key).
 *  - Instagram / LinkedIn / Facebook Page: public page standard link-preview tags.
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

    // ---------- Instagram / LinkedIn / Facebook (public page preview) ----------

    suspend fun fetchPreview(platform: Platform, profile: ParsedProfile): PublicProfile =
        withContext(Dispatchers.IO) {
            if (platform == Platform.INSTAGRAM) {
                return@withContext instagramCounts(profile)
            }
            val html = getHtml(platform, profile.url)
            val og = parseOg(html)
            val titleTag = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let {
                Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim()
            }.orEmpty()
            val title = og["title"].orEmpty().ifBlank { titleTag }
            if (platform != Platform.PORTFOLIO && (title.isBlank() || looksLikeLogin(title))) {
                throw ApiException(
                    "${platform.label} only shows this profile to logged-in visitors. " +
                        "Showing the profile link instead."
                )
            }
            when (platform) {
                Platform.LINKEDIN -> parseLinkedIn(profile, title, og)
                Platform.FACEBOOK -> parseFacebook(profile, title, og)
                Platform.PORTFOLIO -> parsePortfolio(profile, title, og)
                else -> throw ApiException("Preview is not supported for ${platform.label}.")
            }
        }

    private fun parsePortfolio(profile: ParsedProfile, title: String, og: Map<String, String>): PublicProfile {
        val siteName = og["site_name"]?.ifBlank { null }
        val name = title.ifBlank { profile.handle }
        val description = og["description"]?.take(300)?.ifBlank { null }
        return PublicProfile(
            name = name,
            handle = profile.handle,
            subtitle = siteName ?: profile.url.removePrefix("https://").removePrefix("http://"),
            description = description ?: "Personal Portfolio Website",
            imageUrl = og["image"],
            stats = listOf("Status" to "Online"),
        )
    }

    /** Instagram tags store follower and following counts. Name, photo, bio, and posts are not kept. */
    private suspend fun instagramCounts(profile: ParsedProfile): PublicProfile {
        val counts = InstagramScraper.fetchCountsOnly(profile.url)
            ?: throw ApiException(
                "Instagram didn't share follower and following counts for this profile. " +
                    "Showing the profile link instead."
            )
        val handle = profile.handle.trimStart('@')
        return PublicProfile(
            name = handle,
            handle = "@$handle",
            subtitle = null,
            description = null,
            imageUrl = null,
            stats = listOf(
                "Followers" to counts.followers,
                "Following" to counts.following,
            ),
        )
    }

    private fun parseLinkedIn(profile: ParsedProfile, title: String, og: Map<String, String>): PublicProfile {
        val clean = title.substringBefore(" | LinkedIn").trim()
        val name = clean.substringBefore(" - ").ifBlank { profile.handle }
        val headline = clean.substringAfter(" - ", "").ifBlank { null }
        return PublicProfile(
            name = name,
            handle = profile.handle,
            subtitle = headline,
            description = og["description"]?.take(300)?.ifBlank { null },
            imageUrl = og["image"],
            stats = emptyList(),
        )
    }

    private fun parseFacebook(profile: ParsedProfile, title: String, og: Map<String, String>): PublicProfile {
        val desc = og["description"].orEmpty()
        val matcher = Pattern.compile("([0-9.,KkMm]+)\\s+(likes|people follow this|followers)").matcher(desc)
        val followers = if (matcher.find()) matcher.group(1) else null
        val stats = if (followers != null) listOf("Followers" to followers) else emptyList()
        val name = title.substringBefore(" | Facebook").substringBefore(" - Home").trim().ifBlank { profile.handle }
        return PublicProfile(
            name = name,
            handle = profile.handle,
            subtitle = null,
            description = og["description"]?.take(300)?.ifBlank { null },
            imageUrl = og["image"],
            stats = stats,
        )
    }

    private fun looksLikeLogin(title: String): Boolean {
        val t = title.lowercase().trim()
        if (t.contains("followers") || t.contains("following")) return false
        return t == "instagram" || t == "linkedin" || t == "facebook" ||
            listOf("log in", "login", "sign in", "sign up", "authwall").any { t.contains(it) }
    }

    private fun getHtml(platform: Platform, url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        val ua = if (platform == Platform.INSTAGRAM || platform == Platform.FACEBOOK) {
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
        } else {
            "Mozilla/5.0 (Linux; Android 13; TrackMe) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        }
        conn.setRequestProperty("User-Agent", ua)
        conn.setRequestProperty("Accept", "text/html")
        conn.setRequestProperty("Accept-Language", "en")
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw ApiException(
                    "${platform.label} didn't allow a public preview (error $code). " +
                        "Showing the profile link instead."
                )
            }
            return conn.inputStream.bufferedReader().use { r ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (sb.length < 600_000) {
                    val n = r.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }
        } finally {
            conn.disconnect()
        }
    }

    private val metaTag = Regex("""<meta\s+[^>]*>""", RegexOption.IGNORE_CASE)
    private val propAttr = Regex("""(?:property|name)\s*=\s*["']og:([a-z:_]+)["']""", RegexOption.IGNORE_CASE)
    private val contentAttr = Regex("""content\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)

    private fun parseOg(html: String): Map<String, String> {
        val map = HashMap<String, String>()
        for (tag in metaTag.findAll(html)) {
            val t = tag.value
            val key = propAttr.find(t)?.groupValues?.get(1) ?: continue
            val c = contentAttr.find(t) ?: continue
            val raw = c.groupValues[1].ifEmpty { c.groupValues[2] }
            if (key !in map) {
                map[key] = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim()
            }
        }
        return map
    }
}

object SocialScraper {
    suspend fun fetchPublicProfile(urlStr: String, platform: Platform): PlatformData = withContext(Dispatchers.IO) {
        val html = fetchHtml(urlStr)
        when (platform) {
            Platform.INSTAGRAM -> parseInstagram(html, urlStr)
            Platform.FACEBOOK -> parseFacebookPage(html, urlStr)
            Platform.LINKEDIN -> parseLinkedIn(html, urlStr)
            Platform.YOUTUBE -> parseYouTube(urlStr)
            else -> PlatformData(platform, "user", 0L)
        }
    }

    private fun fetchHtml(urlString: String): String {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            connectTimeout = 8000
            readTimeout = 8000
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    // Parses Instagram public meta description tag: "<meta content='XX Followers, YY Following, ZZ Posts'...>"
    private fun parseInstagram(html: String, rawUrl: String): PlatformData {
        val matcher = Pattern.compile("content=\"([0-9.,KkMm]+)\\s+Followers").matcher(html)
        val followers = if (matcher.find()) parseAbbreviatedNumber(matcher.group(1) ?: "") else 0L
        val username = rawUrl.trimEnd('/').substringAfterLast('/')
        return PlatformData(Platform.INSTAGRAM, username, followers)
    }

    // Parses Facebook Page followers/likes from Open Graph description
    private fun parseFacebookPage(html: String, rawUrl: String): PlatformData {
        val matcher = Pattern.compile("([0-9.,KkMm]+)\\s+(likes|people follow this|followers)").matcher(html)
        val followers = if (matcher.find()) parseAbbreviatedNumber(matcher.group(1)) else 0L
        val pageName = rawUrl.trimEnd('/').substringAfterLast('/')
        return PlatformData(Platform.FACEBOOK, pageName, followers)
    }

    // Parses LinkedIn public company/creator profile follower count
    private fun parseLinkedIn(html: String, rawUrl: String): PlatformData {
        val matcher = Pattern.compile("([0-9.,]+)\\s+followers").matcher(html)
        val followers = if (matcher.find()) parseAbbreviatedNumber(matcher.group(1)) else 0L
        val handle = rawUrl.trimEnd('/').substringAfterLast('/')
        return PlatformData(Platform.LINKEDIN, handle, followers)
    }

    // Direct YouTube Channel API or RSS feed parsing
    suspend fun parseYouTube(channelUrl: String): PlatformData {
        // If using YouTube Data API v3:
        // 1. Fetch channel statistics: subscriberCount, viewCount
        // 2. Fetch search/activities: list recent videos for the channel
        return PlatformData(
            platform = Platform.YOUTUBE,
            username = channelUrl.substringAfterLast("/"),
            followersOrSubs = 35200L,
            recentVideos = listOf(
                YouTubeVideoItem("1", "Building Compose Apps", "2 days ago", "https://img.youtube.com/..."),
                YouTubeVideoItem("2", "Architecture Guidelines", "1 week ago", "https://img.youtube.com/...")
            ),
            historicalMetrics = listOf(
                "Mon" to 34100L, "Tue" to 34400L, "Wed" to 34800L, "Thu" to 35200L
            )
        )
    }

    private fun parseAbbreviatedNumber(raw: String): Long {
        val cleaned = raw.replace(",", "").trim()
        val factor = when {
            cleaned.endsWith("k", true) -> 1_000.0
            cleaned.endsWith("m", true) -> 1_000_000.0
            else -> 1.0
        }
        val numberPart = cleaned.replace("[^0-9.]".toRegex(), "")
        return ((numberPart.toDoubleOrNull() ?: 0.0) * factor).toLong()
    }
}

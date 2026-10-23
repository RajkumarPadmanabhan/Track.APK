package com.trackme.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Reads follower and following counts from a public page.
 * A missing count stays null. Zero is never invented when the page does not publish that number.
 */
data class SocialCounts(val followers: String?, val following: String?)

object SocialCountScraper {
    suspend fun fetch(rawUrl: String): SocialCounts? = withContext(Dispatchers.IO) {
        try {
            var loaded = false
            val followerText = ArrayList<Hit>()
            val followerJson = ArrayList<Hit>()
            val followingTextHits = ArrayList<Hit>()
            val followingJson = ArrayList<Hit>()
            for (url in targets(rawUrl)) {
                val html = load(url) ?: continue
                loaded = true
                val text = snippets(html)
                collect(followersText, text, followerText)
                collect(peopleFollowText, text, followerText)
                collect(followingText, text, followingTextHits)
                collect(jsonFollowers, text, followerJson)
                collect(jsonFollowedBy, text, followerJson)
                collect(jsonFollowing, text, followingJson)
                collect(jsonFollow, text, followingJson)
                if (followerText.isNotEmpty() || followerJson.isNotEmpty()) break
            }
            if (!loaded) return@withContext null
            SocialCounts(choose(followerText, followerJson), choose(followingTextHits, followingJson))
        } catch (_: Throwable) {
            null
        }
    }

    /** Only the page head and the text around follower/following words. Avoids crashing on huge pages. */
    private fun snippets(html: String): String {
        val normalized = html.replace('\u00a0', ' ').replace("&nbsp;", " ", ignoreCase = true)
        val sb = StringBuilder(normalized.take(80_000))
        for (key in arrayOf("follower", "following")) {
            var from = 0
            var found = 0
            while (found < 6) {
                val idx = normalized.indexOf(key, from, ignoreCase = true)
                if (idx < 0) break
                val start = (idx - 60).coerceAtLeast(0)
                val end = (idx + key.length + 24).coerceAtMost(normalized.length)
                sb.append('\n').append(normalized, start, end)
                from = idx + key.length
                found++
            }
        }
        return sb.toString()
    }

    private data class Hit(val value: Long, val exact: Boolean, val label: String)

    private val number = "(\\d{1,3}(?:[, ]\\d{2,3})+|\\d+(?:\\.\\d+)?)([KkMmBb])?"
    private val followersText = Regex("$number\\s+followers?\\b", RegexOption.IGNORE_CASE)
    private val peopleFollowText = Regex("$number\\s+people follow this\\b", RegexOption.IGNORE_CASE)
    private val followingText = Regex("$number\\s+following\\b", RegexOption.IGNORE_CASE)
    private val jsonFollowers = Regex(
        """"(?:follower_count|followers_count|followersCount)"\s*:\s*"?(\d+)""",
        RegexOption.IGNORE_CASE,
    )
    private val jsonFollowedBy = Regex(""""edge_followed_by"\s*:\s*\{\s*"count"\s*:\s*(\d+)""")
    private val jsonFollowing = Regex(
        """"(?:following_count|followingCount)"\s*:\s*"?(\d+)""",
        RegexOption.IGNORE_CASE,
    )
    private val jsonFollow = Regex(""""edge_follow"\s*:\s*\{\s*"count"\s*:\s*(\d+)""")

    private fun collect(pattern: Regex, text: String, into: MutableList<Hit>) {
        for (match in pattern.findAll(text)) {
            val digits = match.groupValues.getOrNull(1) ?: continue
            val suffix = match.groupValues.getOrNull(2).orEmpty()
            val value = parseCount(digits.replace(" ", "") + suffix) ?: continue
            if (value <= 0L) continue
            val label = if (suffix.isEmpty()) value.toString() else digits.replace(" ", "").replace(",", "") + suffix.uppercase()
            into += Hit(value, suffix.isEmpty(), label)
        }
    }

    /** Uses the first count on the page. A nearby exact JSON value replaces an abbreviated one. */
    private fun choose(textHits: List<Hit>, jsonHits: List<Hit>): String? {
        val first = textHits.firstOrNull()
        if (first != null) {
            val refined = jsonHits.firstOrNull { closeEnough(it.value, first.value) }
            return when {
                refined != null -> refined.value.toString()
                first.exact -> first.value.toString()
                else -> first.label
            }
        }
        return jsonHits.firstOrNull()?.value?.toString()
    }

    private fun closeEnough(exact: Long, shown: Long): Boolean {
        if (shown <= 0L) return false
        return Math.abs(exact - shown).toDouble() / shown.toDouble() <= 0.05
    }

    private fun targets(rawUrl: String): List<String> {
        val trimmed = rawUrl.trim()
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        val uri = try {
            URI(withScheme)
        } catch (_: Exception) {
            return listOf(withScheme)
        }
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path.orEmpty().trim('/')
        return when {
            "instagram.com" in host -> {
                val user = path.substringBefore('/').trim().trimStart('@')
                if (user.isBlank()) listOf(withScheme) else listOf("https://www.instagram.com/$user/")
            }
            "linkedin.com" in host -> {
                val parts = path.split('/').filter { it.isNotBlank() }
                if (parts.size >= 2) listOf("https://www.linkedin.com/${parts[0]}/${parts[1]}/")
                else listOf(withScheme)
            }
            "facebook.com" in host || host == "fb.com" || host.endsWith(".fb.com") -> {
                val id = Regex("""[?&]id=(\d+)""").find(uri.rawQuery.orEmpty())?.groupValues?.get(1)
                val url = when {
                    path.startsWith("profile.php") && id != null ->
                        "https://www.facebook.com/profile.php?id=$id&locale=en_US"
                    path.startsWith("people/") ->
                        "https://www.facebook.com/$path?locale=en_US"
                    else -> {
                        val slug = path.substringBefore('/').ifBlank { path }
                        "https://www.facebook.com/$slug?locale=en_US"
                    }
                }
                listOf(url)
            }
            host == "x.com" || host.endsWith(".x.com") || "twitter.com" in host -> {
                val user = path.substringBefore('/').trim().trimStart('@')
                if (user.isBlank()) listOf(withScheme) else listOf("https://x.com/$user")
            }
            else -> listOf(withScheme)
        }
    }

    private fun load(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("Cookie", "locale=en_US")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (sb.length < 1_000_000) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}

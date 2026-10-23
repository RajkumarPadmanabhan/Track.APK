package com.trackme.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/**
 * Reads follower and following counts from a public page.
 * A missing count stays null. Zero is never invented when the page does not publish that number.
 */
data class SocialCounts(val followers: String?, val following: String?)

object SocialCountScraper {
    private const val GOOGLEBOT = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
    private const val FACEBOOK_HIT = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
    private const val MOBILE =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

    suspend fun fetch(rawUrl: String): SocialCounts? = withContext(Dispatchers.IO) {
        try {
            var loaded = false
            var best = SocialCounts(null, null)
            for (url in targets(rawUrl)) {
                for (agent in agentsFor(url)) {
                    val html = load(url, agent) ?: continue
                    loaded = true
                    val found = readCounts(html)
                    best = SocialCounts(
                        followers = best.followers ?: found.followers,
                        following = best.following ?: found.following,
                    )
                    if (best.followers != null && best.following != null) return@withContext best
                }
            }
            if (best.followers == null) {
                fromPublicSnippet(rawUrl)?.let { found ->
                    loaded = true
                    best = SocialCounts(found.followers ?: best.followers, found.following ?: best.following)
                }
            }
            if (!loaded) null else best
        } catch (_: Throwable) {
            null
        }
    }

    private fun agentsFor(url: String): List<String> {
        return if ("facebook.com" in url || "fb.com" in url) {
            listOf(FACEBOOK_HIT, GOOGLEBOT, MOBILE)
        } else {
            listOf(GOOGLEBOT, MOBILE)
        }
    }

    /** The profile's own count is in the description. Later "followers" mentions are other people. */
    private fun readCounts(html: String): SocialCounts {
        var followers: String? = null
        var following: String? = null
        for (desc in metaValues(html)) {
            val plain = strip(decode(desc))
            if (followers == null) followers = firstCount(plain, "followers?")
            if (following == null) following = firstCount(plain, "following")
        }
        if (followers == null) {
            followers = jsonNumber(html, "followerCount")
                ?: jsonNumber(html, "followersCount")
                ?: jsonNumber(html, "followers_count")
        }
        if (following == null) {
            following = jsonNumber(html, "followingCount") ?: jsonNumber(html, "following_count")
        }
        if (followers == null) {
            followers = nearby(html, "followers") ?: nearby(html, "follower")
        }
        if (followers == null) followers = nearbyPhrase(html, "people follow this")
        if (following == null) following = nearby(html, "following")
        return SocialCounts(followers, following)
    }

    /** Public search snippet, used when the page itself is a sign-in wall. */
    private fun fromPublicSnippet(pageUrl: String): SocialCounts? {
        val host = try {
            URI(pageUrl).host?.lowercase().orEmpty()
        } catch (_: Exception) {
            ""
        }
        if ("linkedin.com" !in host && "facebook.com" !in host && "fb.com" !in host) return null
        val slug = pageSlug(pageUrl) ?: return null
        val query = URLEncoder.encode("\"$slug\" followers", "UTF-8")
        val pages = listOf(
            "https://html.duckduckgo.com/html/?q=$query",
            "https://www.bing.com/search?q=$query",
        )
        for (page in pages) {
            val html = load(page, MOBILE) ?: continue
            val plain = strip(decode(html))
            val at = plain.indexOf(slug, ignoreCase = true)
            if (at < 0) continue
            val window = plain.substring((at - 40).coerceAtLeast(0), (at + 240).coerceAtMost(plain.length))
            val followers = firstCount(window, "followers?") ?: continue
            return SocialCounts(followers, firstCount(window, "following"))
        }
        return null
    }

    private fun pageSlug(pageUrl: String): String? {
        val withScheme = if (pageUrl.startsWith("http://") || pageUrl.startsWith("https://")) pageUrl else "https://$pageUrl"
        val uri = try {
            URI(withScheme)
        } catch (_: Exception) {
            return null
        }
        val skip = setOf("in", "company", "school", "pages", "people", "profile.php")
        val slug = uri.path.orEmpty().trim('/').split('/').lastOrNull { it.isNotBlank() && it.lowercase() !in skip }
            ?.substringBefore('?')
            ?: return null
        if (slug.length < 3) return null
        return slug
    }

    private fun jsonNumber(html: String, key: String): String? {
        var from = 0
        var seen = 0
        while (seen < 6) {
            val idx = html.indexOf(key, from, ignoreCase = true)
            if (idx < 0) return null
            val slice = html.substring(idx, (idx + key.length + 48).coerceAtMost(html.length))
            val match = Regex("""[:=]\s*"?(\d{1,12})""").find(slice)
            val value = match?.groupValues?.get(1)?.toLongOrNull()
            if (value != null && value > 0L) return value.toString()
            from = idx + key.length
            seen++
        }
        return null
    }

    private fun strip(text: String): String = text.replace(Regex("<[^>]{0,300}>"), " ")

    private fun metaValues(html: String): List<String> {
        val headEnd = html.indexOf("</head>", ignoreCase = true).let { end ->
            if (end < 0) html.length.coerceAtMost(600_000) else end.coerceAtMost(html.length)
        }
        val head = html.substring(0, headEnd)
        val values = ArrayList<String>()
        var from = 0
        while (from < head.length) {
            val meta = head.indexOf("<meta", from, ignoreCase = true)
            if (meta < 0) break
            val end = head.indexOf('>', meta)
            if (end < 0) break
            val tag = head.substring(meta, end + 1)
            if (tag.contains("description", ignoreCase = true)) {
                contentAttr(tag)?.let { values += decode(it) }
            }
            from = end + 1
        }
        return values
    }

    private fun contentAttr(tag: String): String? {
        val key = "content="
        val at = tag.indexOf(key, ignoreCase = true)
        if (at < 0) return null
        val q = tag.getOrNull(at + key.length) ?: return null
        if (q != '"' && q != '\'') return null
        val start = at + key.length + 1
        val end = tag.indexOf(q, start)
        if (end < 0) return null
        return tag.substring(start, end)
    }

    private fun nearby(html: String, word: String): String? {
        val pattern = if (word == "following") "following" else "followers?"
        var from = 0
        var seen = 0
        while (seen < 30) {
            val idx = html.indexOf(word, from, ignoreCase = true)
            if (idx < 0) return null
            val start = (idx - 120).coerceAtLeast(0)
            val window = strip(decode(html.substring(start, idx + word.length)))
            val hit = firstCount(window, pattern)
            if (hit != null) return hit
            from = idx + word.length
            seen++
        }
        return null
    }

    private fun nearbyPhrase(html: String, phrase: String): String? {
        val idx = html.indexOf(phrase, ignoreCase = true)
        if (idx < 0) return null
        val start = (idx - 120).coerceAtLeast(0)
        return firstCount(strip(decode(html.substring(start, idx + phrase.length))), "people follow this")
    }

    private val amount = """(\d{1,3}(?:[, ]\d{2,3})+|\d+(?:\.\d+)?)([KMBkmb])?"""

    private fun firstCount(text: String, word: String): String? {
        val match = Regex("$amount\\s+$word\\b", RegexOption.IGNORE_CASE).find(text) ?: return null
        val digits = match.groupValues[1].replace(" ", "").replace(",", "")
        val suffix = match.groupValues[2]
        val value = parseCount(digits + suffix) ?: return null
        if (value <= 0L) return null
        return if (suffix.isEmpty()) value.toString() else digits + suffix.uppercase()
    }

    private fun decode(raw: String): String {
        val named = raw.replace("&nbsp;", " ", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&#039;", "'", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
        val out = StringBuilder()
        var i = 0
        while (i < named.length) {
            if (named.startsWith("&#", i)) {
                val hex = named.startsWith("&#x", i, ignoreCase = true)
                val numStart = i + if (hex) 3 else 2
                val semi = named.indexOf(';', numStart)
                if (semi > numStart) {
                    val code = if (hex) named.substring(numStart, semi).toIntOrNull(16)
                    else named.substring(numStart, semi).toIntOrNull()
                    if (code != null) {
                        out.append(code.toChar())
                        i = semi + 1
                        continue
                    }
                }
            }
            out.append(named[i])
            i++
        }
        return out.toString().replace('\u00a0', ' ').replace('\u202f', ' ')
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
                val page = when {
                    path.startsWith("profile.php") && id != null -> "profile.php?id=$id"
                    path.startsWith("people/") -> path
                    else -> path.substringBefore('/').ifBlank { path }
                }
                val web = if (page.startsWith("profile.php")) {
                    "https://www.facebook.com/$page&locale=en_US"
                } else {
                    "https://www.facebook.com/$page?locale=en_US"
                }
                val basic = if (page.startsWith("profile.php")) {
                    "https://mbasic.facebook.com/$page&locale=en_US"
                } else {
                    "https://mbasic.facebook.com/$page?locale=en_US"
                }
                val href = URLEncoder.encode(web.substringBefore('?'), "UTF-8")
                listOf(
                    web,
                    basic,
                    "https://www.facebook.com/plugins/page.php?href=$href&locale=en_US",
                )
            }
            host == "x.com" || host.endsWith(".x.com") || "twitter.com" in host -> {
                val user = path.substringBefore('/').trim().trimStart('@')
                if (user.isBlank()) listOf(withScheme) else listOf("https://x.com/$user")
            }
            else -> listOf(withScheme)
        }
    }

    private fun load(url: String, agent: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("User-Agent", agent)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Cookie", "locale=en_US")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (sb.length < 2_000_000) {
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

package com.trackme.app.data

data class ParsedProfile(val handle: String, val url: String)

/**
 * Supported social platforms.
 * fullScan   = true  -> repo platform with an official open API (GitHub, GitLab).
 * hasPreview = true  -> TrackMe reads follower and following counts when the page publishes them.
 */
enum class Platform(
    val label: String,
    val example: String,
    val fullScan: Boolean,
    val note: String?,
    private val urlRegex: Regex,
    private val handleRegex: Regex,
    private val baseUrl: String,
) {
    GITHUB(
        "GitHub", "https://github.com/username", true, null,
        Regex("""(?:https?://)?(?:www\.)?github\.com/([A-Za-z0-9][A-Za-z0-9-]*)""", RegexOption.IGNORE_CASE),
        Regex("^[A-Za-z0-9][A-Za-z0-9-]*$"),
        "https://github.com/",
    ),
    GITLAB(
        "GitLab", "https://gitlab.com/username", true, null,
        Regex("""(?:https?://)?(?:www\.)?gitlab\.com/([A-Za-z0-9_][A-Za-z0-9_.-]*)""", RegexOption.IGNORE_CASE),
        Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]*$"),
        "https://gitlab.com/",
    ),
    INSTAGRAM(
        "Instagram", "https://instagram.com/username", false,
        "Shows follower and following counts when the page publishes them.",
        Regex("""(?:https?://)?(?:www\.)?instagram\.com/([A-Za-z0-9._]+)""", RegexOption.IGNORE_CASE),
        Regex("^[A-Za-z0-9._]+$"),
        "https://www.instagram.com/",
    ),
    LINKEDIN(
        "LinkedIn", "https://linkedin.com/in/username", false,
        "Shows follower and following counts when the page publishes them.",
        Regex("""(?:https?://)?(?:(?:www|m)\.)?linkedin\.com/(?:in|company|school)/([A-Za-z0-9_%.-]+)""", RegexOption.IGNORE_CASE),
        Regex("^[A-Za-z0-9_%.-]+$"),
        "https://www.linkedin.com/in/",
    ),
    X(
        "X", "https://x.com/username", false,
        "Shows follower and following counts when the page publishes them.",
        Regex("""(?:https?://)?(?:www\.)?(?:x|twitter)\.com/([A-Za-z0-9_]+)""", RegexOption.IGNORE_CASE),
        Regex("^[A-Za-z0-9_]{1,15}$"),
        "https://x.com/",
    ),
    YOUTUBE(
        "YouTube", "https://youtube.com/@channel", false,
        "Channel stats come from the official YouTube Data API using your own free API key " +
            "(Google Cloud Console > enable YouTube Data API v3 > create an API key).",
        Regex(
            """(?:https?://)?(?:www\.)?youtube\.com/(@[A-Za-z0-9_.-]+|(?:channel|c|user)/[A-Za-z0-9_-]+)""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("^@?[A-Za-z0-9_.-]+$"),
        "https://www.youtube.com/",
    ),
    FACEBOOK(
        "Facebook", "https://facebook.com/pagename", false,
        "Shows follower and following counts for a profile or page when Facebook publishes them.",
        Regex(
            """(?:https?://)?(?:(?:www|m|mbasic|web)\.)?(?:facebook\.com|fb\.com)/((?:profile\.php\?id=\d+|people/[A-Za-z0-9.\-]+/\d+|[A-Za-z0-9.%_\-]+))""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("^[A-Za-z0-9.%_-]+$"),
        "https://www.facebook.com/",
    ),
    PORTFOLIO(
        "Portfolio Website", "https://myportfolio.com", false,
        "Shows follower and following counts when that website publishes them.",
        Regex("""(?:https?://)?([A-Za-z0-9_.-]+\.[A-Za-z]{2,}(?:/[^\s]*)?)""", RegexOption.IGNORE_CASE),
        Regex("^[A-Za-z0-9_.-]+\\.[A-Za-z]{2,}$"),
        "https://",
    );

    val hasPreview: Boolean
        get() = this != GITHUB && this != GITLAB

    fun matches(text: String): Boolean = urlRegex.containsMatchIn(text)

    /** Accepts a profile URL (any common form) or a bare handle. */
    fun parse(input: String): ParsedProfile? {
        val t = input.trim().removeSuffix("/")
        if (t.isEmpty()) return null

        urlRegex.find(t)?.let { m ->
            val handle = m.groupValues[1]
            if (this == FACEBOOK) {
                val id = Regex("""profile\.php\?id=(\d+)""", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)
                    ?: Regex("""/people/[^/?#]+/(\d+)""", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)
                if (id != null) {
                    return ParsedProfile(id, "https://www.facebook.com/profile.php?id=$id")
                }
                val slug = handle.substringBefore('?').substringAfterLast('/').trim('/')
                return ParsedProfile(slug, "https://www.facebook.com/$slug")
            }
            val clean = m.value
                .replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^www\\.", RegexOption.IGNORE_CASE), "")
            return ParsedProfile(handle, "https://$clean")
        }

        val bare = t.removePrefix("@")
        if (handleRegex.matches(bare)) {
            val path = if (this == YOUTUBE && !bare.startsWith("@")) "@$bare" else bare
            val url = if (this == PORTFOLIO) "https://$bare" else baseUrl + path
            return ParsedProfile(bare, url)
        }
        return null
    }
}

/** Finds which platform a pasted/shared link belongs to. */
fun detectPlatform(text: String): Platform? = Platform.values().firstOrNull { it.matches(text) }

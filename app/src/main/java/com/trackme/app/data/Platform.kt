package com.trackme.app.data

data class ParsedProfile(val handle: String, val url: String)

/**
 * Supported social platforms.
 * fullScan   = true  -> repo platform with an official open API (GitHub, GitLab).
 * hasPreview = true  -> TrackMe also tries to read public profile details (YouTube API, or the
 *                       public page preview for Instagram / LinkedIn) and falls back to a link card.
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
        "Reads follower and following counts only. " +
            "Instagram sometimes hides them; you then get the profile link card instead.",
        Regex("""(?:https?://)?(?:www\.)?instagram\.com/([A-Za-z0-9._]+)""", RegexOption.IGNORE_CASE),
        Regex("^[A-Za-z0-9._]+$"),
        "https://www.instagram.com/",
    ),
    LINKEDIN(
        "LinkedIn", "https://linkedin.com/in/username", false,
        "Reads the public profile preview (name, headline, photo) when LinkedIn allows it. " +
            "LinkedIn blocks most automated reads; you then get the profile link card instead.",
        Regex("""(?:https?://)?(?:www\.)?linkedin\.com/(?:in|company)/([A-Za-z0-9_%.-]+)""", RegexOption.IGNORE_CASE),
        Regex("^[A-Za-z0-9_%.-]+$"),
        "https://www.linkedin.com/in/",
    ),
    X(
        "X", "https://x.com/username", false,
        "X restricts profile data behind a paid API, so TrackMe saves the profile link and lets " +
            "you open, share or copy it.",
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
        "Facebook Page", "https://facebook.com/pagename", false,
        "Reads public Facebook Page followers and likes when available. " +
            "If Facebook blocks access, showing the page link instead.",
        Regex("""(?:https?://)?(?:www\.)?facebook\.com/([A-Za-z0-9.%_-]+)""", RegexOption.IGNORE_CASE),
        Regex("^[A-Za-z0-9.%_-]+$"),
        "https://www.facebook.com/",
    ),
    PORTFOLIO(
        "Portfolio Website", "https://myportfolio.com", false,
        "Fetches site title, OpenGraph metadata, and status directly from your personal portfolio website.",
        Regex("""(?:https?://)?([A-Za-z0-9_.-]+\.[A-Za-z]{2,}(?:/[^\s]*)?)""", RegexOption.IGNORE_CASE),
        Regex("^[A-Za-z0-9_.-]+\\.[A-Za-z]{2,}$"),
        "https://",
    );

    val hasPreview: Boolean
        get() = this == INSTAGRAM || this == LINKEDIN || this == YOUTUBE || this == FACEBOOK || this == PORTFOLIO

    fun matches(text: String): Boolean = urlRegex.containsMatchIn(text)

    /** Accepts a profile URL (any common form) or a bare handle. */
    fun parse(input: String): ParsedProfile? {
        val t = input.trim().removeSuffix("/")
        if (t.isEmpty()) return null

        urlRegex.find(t)?.let { m ->
            val clean = m.value
                .replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^www\\.", RegexOption.IGNORE_CASE), "")
            return ParsedProfile(m.groupValues[1], "https://$clean")
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

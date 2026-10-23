package com.trackme.app.data

data class GhUser(
    val login: String,
    val name: String?,
    val avatarUrl: String,
    val bio: String?,
    val followers: Int,
    val following: Int,
    val publicRepos: Int,
    val htmlUrl: String,
    val createdAt: String,
    val location: String?,
)

data class GhRepo(
    val name: String,
    val fullName: String,
    val description: String?,
    val language: String?,
    val stars: Int,
    val forks: Int,
    val sizeKb: Int,
    val updatedAt: String,
    val pushedAt: String,
    val htmlUrl: String,
    val defaultBranch: String,
    val isFork: Boolean,
    val archived: Boolean,
    val zipUrl: String,
)

/** Public details read from a social profile (YouTube, Instagram, LinkedIn). */
data class PublicProfile(
    val name: String,
    val handle: String,
    val subtitle: String?,
    val description: String?,
    val imageUrl: String?,
    val stats: List<Pair<String, String>>,
)

data class YouTubeVideoItem(
    val id: String,
    val title: String,
    val publishedAt: String,
    val thumbnailUrl: String,
    val viewCount: Long = 0L,
    val likeCount: Long = 0L
)

data class PlatformData(
    val platform: Platform,
    val username: String,
    val followersOrSubs: Long,
    val secondaryCount: Long = 0L, // e.g. Total views, repositories, etc.
    val recentVideos: List<YouTubeVideoItem> = emptyList(),
    val historicalMetrics: List<Pair<String, Long>> = emptyList() // For graphing (date -> count)
)

data class InstagramProfileStats(
    val username: String,
    val profileUrl: String,
    val followers: String = "0",
    val following: String = "0",
    val posts: String = "0",
    val lastUpdated: Long = System.currentTimeMillis()
)

enum class SortMode(val label: String) {
    BEST("Best match"),
    STARS("Stars"),
    UPDATED("Recently updated"),
    FORKS("Forks"),
    SIZE("Size"),
    NAME("Name A-Z"),
}

data class GhRelease(
    val name: String,
    val tagName: String,
    val publishedAt: String,
    val body: String?,
    val htmlUrl: String,
    val zipUrl: String? = null,
)

data class GhCollaborator(
    val login: String,
    val avatarUrl: String?,
    val htmlUrl: String,
    val contributions: Int = 0,
)

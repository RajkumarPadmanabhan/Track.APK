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
    val overview: String? = null,
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

/** One heading from an uploaded PDF, with the lines that belong to it. */
data class PortfolioSection(
    val heading: String,
    val category: String,
    val items: List<String>,
)

/** A simple portfolio built only from the text found inside an uploaded PDF. */
data class PdfPortfolio(
    val fileName: String,
    val pageCount: Int,
    val name: String,
    val headline: String?,
    val email: String?,
    val phone: String?,
    val links: List<String>,
    val summary: String?,
    val sections: List<PortfolioSection>,
    val skills: List<String>,
    val usedOcr: Boolean,
    val uploadedFileId: String? = null,
    val originalSize: Long = 0L,
    val storedSize: Long = 0L,
    val sha256: String? = null,
)

/** One PDF saved inside the app. The file on disk has a generated name, never the user's name. */
data class UploadedFile(
    val id: String,
    val displayName: String,
    val storedName: String,
    val originalSize: Long,
    val storedSize: Long,
    val pageCount: Int,
    val sha256: String,
    val uploadedAt: Long,
    val portfolioName: String?,
    val mimeType: String = "application/octet-stream",
    val extension: String = "",
    val folder: String = "",
)

/** Public details read from a social profile (YouTube, Instagram, LinkedIn). */
data class PublicProfile(
    val name: String,
    val handle: String,
    val subtitle: String?,
    val description: String?,
    val imageUrl: String?,
    val stats: List<Pair<String, String>>,
    val recentVideos: List<YouTubeVideoItem> = emptyList(),
    val shop: List<ShopEntry> = emptyList(),
    val portfolio: PdfPortfolio? = null,
)

/** One cart product or one order read from Amazon or Flipkart. */
data class ShopEntry(
    val kind: String,
    val title: String,
    val amountLabel: String,
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
    val followers: String = "",
    val following: String = "",
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

data class GhActionRun(
    val id: Long,
    val name: String,
    val displayTitle: String,
    val status: String,
    val conclusion: String?,
    val event: String,
    val createdAt: String,
    val updatedAt: String,
    val htmlUrl: String,
    val commitMessage: String? = null,
    val authorName: String? = null,
)

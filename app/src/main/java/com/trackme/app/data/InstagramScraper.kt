package com.trackme.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object InstagramScraper {
    suspend fun fetchCountsOnly(rawUrl: String): InstagramProfileStats? = withContext(Dispatchers.IO) {
        val username = rawUrl.trim()
            .substringBefore("?")
            .substringBefore("#")
            .trimEnd('/')
            .substringAfterLast("/")
            .trimStart('@')
        if (username.isBlank()) return@withContext null
        val counts = SocialCountScraper.fetch(rawUrl) ?: return@withContext null
        if (counts.followers == null && counts.following == null) return@withContext null
        InstagramProfileStats(
            username = username,
            profileUrl = "https://www.instagram.com/$username/",
            followers = counts.followers.orEmpty(),
            following = counts.following.orEmpty(),
            lastUpdated = System.currentTimeMillis(),
        )
    }
}

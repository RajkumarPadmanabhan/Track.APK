package com.trackme.app.data

data class GitContributionDay(
    val date: String,
    val count: Int // 0, 1, 2, 3, 4+ (determines green intensity)
)

data class GitDeveloperStats(
    val username: String,
    val openPRs: Int = 0,
    val openIssues: Int = 0,
    val eventsToday: Int = 0,
    // 7 weeks of daily contribution data (49 days)
    val recentContributions: List<GitContributionDay> = emptyList()
)

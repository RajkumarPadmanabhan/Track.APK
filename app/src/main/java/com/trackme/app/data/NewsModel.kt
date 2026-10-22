package com.trackme.app.data

data class UserNewsPost(
    val id: String,
    val title: String,
    val description: String,
    val sourceName: String,
    val sourceUrl: String,
    val publishedAt: String,
    val imageUrl: String? = null,
    val categoryTag: String = "Trending"
)

package com.trackme.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object GitLabApi {
    private const val BASE = "https://gitlab.com/api/v4"

    /** Returns the user and their numeric GitLab id. */
    suspend fun fetchUser(username: String): Pair<GhUser, Long> = withContext(Dispatchers.IO) {
        val list = JSONArray(Http.get("$BASE/users?username=${URLEncoder.encode(username, "UTF-8")}"))
        if (list.length() == 0) throw ApiException("GitLab user not found.")
        val basic = list.getJSONObject(0)
        val id = basic.getLong("id")
        // The detail endpoint has bio, location and follower counts; fall back to the basic record.
        val d = runCatching { JSONObject(Http.get("$BASE/users/$id")) }.getOrNull() ?: basic
        val user = GhUser(
            login = d.optString("username", username),
            name = d.str("name"),
            avatarUrl = d.optString("avatar_url"),
            bio = d.str("bio"),
            followers = d.optInt("followers"),
            following = d.optInt("following"),
            publicRepos = 0,
            htmlUrl = d.optString("web_url", "https://gitlab.com/$username"),
            createdAt = d.optString("created_at"),
            location = d.str("location"),
        )
        user to id
    }

    suspend fun fetchRepos(userId: Long): List<GhRepo> = withContext(Dispatchers.IO) {
        val all = mutableListOf<GhRepo>()
        for (page in 1..10) {
            val arr = JSONArray(
                Http.get("$BASE/users/$userId/projects?per_page=100&page=$page&order_by=last_activity_at&sort=desc")
            )
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val path = o.getString("path_with_namespace")
                val slug = o.optString("path", o.getString("name"))
                val branch = o.str("default_branch") ?: "main"
                val activity = o.optString("last_activity_at")
                all += GhRepo(
                    name = o.getString("name"),
                    fullName = path,
                    description = o.str("description"),
                    language = null, // GitLab's project list does not include a language
                    stars = o.optInt("star_count"),
                    forks = o.optInt("forks_count"),
                    sizeKb = 0,
                    updatedAt = activity,
                    pushedAt = activity,
                    htmlUrl = o.getString("web_url"),
                    defaultBranch = branch,
                    isFork = o.has("forked_from_project"),
                    archived = o.optBoolean("archived"),
                    zipUrl = "https://gitlab.com/$path/-/archive/$branch/$slug-$branch.zip",
                )
            }
            if (arr.length() < 100) break
        }
        all
    }

    suspend fun fetchActivity(userId: Long, username: String): Map<String, Int> = withContext(Dispatchers.IO) {
        // Public calendar for the last year: {"2026-10-21": 3, ...}
        HttpHtml.get(
            "https://gitlab.com/users/${URLEncoder.encode(username, "UTF-8")}/calendar.json",
            "application/json",
        )?.let { body ->
            try {
                val o = JSONObject(body)
                val year = HashMap<String, Int>()
                for (k in o.keys()) year[k] = o.optInt(k)
                if (year.isNotEmpty()) return@withContext year
            } catch (_: Exception) {
            }
        }
        val map = HashMap<String, Int>()
        try {
            for (page in 1..3) {
                val arr = JSONArray(Http.get("$BASE/users/$userId/events?per_page=100&page=$page"))
                if (arr.length() == 0) break
                for (i in 0 until arr.length()) {
                    val day = Days.fromIso(arr.getJSONObject(i).optString("created_at"))
                    if (day.length == 10) map[day] = (map[day] ?: 0) + 1
                }
                if (arr.length() < 100) break
            }
        } catch (_: Exception) {
            // Optional data, ignore failures.
        }
        map
    }

    suspend fun fetchReleases(fullName: String): List<GhRelease> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(fullName, "UTF-8")
            val arr = JSONArray(Http.get("$BASE/projects/$encoded/releases?per_page=50"))
            val list = mutableListOf<GhRelease>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.str("name") ?: o.optString("tag_name")
                val tag = o.optString("tag_name")
                val published = o.optString("released_at")
                val body = o.str("description")
                val links = o.optJSONObject("_links")
                val webUrl = links?.optString("self") ?: "https://gitlab.com/$fullName/-/releases"
                list += GhRelease(
                    name = name,
                    tagName = tag,
                    publishedAt = published,
                    body = body,
                    htmlUrl = webUrl,
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchCollaborators(fullName: String): List<GhCollaborator> = withContext(Dispatchers.IO) {
        val list = mutableListOf<GhCollaborator>()
        try {
            val encoded = URLEncoder.encode(fullName, "UTF-8")
            val jsonStr = try {
                Http.get("$BASE/projects/$encoded/members?per_page=50")
            } catch (_: Exception) {
                Http.get("$BASE/projects/$encoded/repository/contributors?per_page=50")
            }
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val login = o.str("username") ?: o.str("name") ?: o.str("email") ?: "Contributor"
                list += GhCollaborator(
                    login = login,
                    avatarUrl = o.str("avatar_url"),
                    htmlUrl = o.optString("web_url", "https://gitlab.com/$fullName"),
                    contributions = o.optInt("commits"),
                )
            }
        } catch (_: Exception) { }
        list
    }
}

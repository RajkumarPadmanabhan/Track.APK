package com.trackme.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object GitHubApi {

    suspend fun fetchUser(username: String): GhUser = withContext(Dispatchers.IO) {
        val o = JSONObject(Http.get("https://api.github.com/users/$username"))
        GhUser(
            login = o.getString("login"),
            name = o.str("name"),
            avatarUrl = o.optString("avatar_url"),
            bio = o.str("bio"),
            followers = o.optInt("followers"),
            following = o.optInt("following"),
            publicRepos = o.optInt("public_repos"),
            htmlUrl = o.getString("html_url"),
            createdAt = o.optString("created_at"),
            location = o.str("location"),
        )
    }

    /** Fetches every public repo (paginated, 100 per page, up to 1000). */
    suspend fun fetchRepos(username: String): List<GhRepo> = withContext(Dispatchers.IO) {
        val all = mutableListOf<GhRepo>()
        for (page in 1..10) {
            val arr = JSONArray(
                Http.get("https://api.github.com/users/$username/repos?per_page=100&page=$page&sort=pushed")
            )
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val fullName = o.getString("full_name")
                val branch = o.optString("default_branch", "main")
                all += GhRepo(
                    name = o.getString("name"),
                    fullName = fullName,
                    description = o.str("description"),
                    language = o.str("language"),
                    stars = o.optInt("stargazers_count"),
                    forks = o.optInt("forks_count"),
                    sizeKb = o.optInt("size"),
                    updatedAt = o.optString("updated_at"),
                    pushedAt = o.optString("pushed_at"),
                    htmlUrl = o.getString("html_url"),
                    defaultBranch = branch,
                    isFork = o.optBoolean("fork"),
                    archived = o.optBoolean("archived"),
                    zipUrl = "https://github.com/$fullName/archive/refs/heads/$branch.zip",
                )
            }
            if (arr.length() < 100) break
        }
        all
    }

    /**
     * The full-year contribution graph, read from GitHub's public contributions page
     * (the same data as the green graph on a profile). Empty map if it can't be read.
     */
    private fun fetchContributions(username: String): Map<String, Int> {
        val html = HttpHtml.get("https://github.com/users/$username/contributions") ?: return emptyMap()
        val cell = Regex("<td[^>]*data-date=\"(\\d{4}-\\d{2}-\\d{2})\"[^>]*>")
        val idRe = Regex("id=\"([^\"]+)\"")
        val levelRe = Regex("data-level=\"(\\d)\"")
        val tipRe = Regex("<tool-tip[^>]*for=\"([^\"]+)\"[^>]*>\\s*([^<]*)<")

        val tips = HashMap<String, Int>()
        for (m in tipRe.findAll(html)) {
            val text = m.groupValues[2].trim()
            tips[m.groupValues[1]] = Regex("^(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
        val levelGuess = intArrayOf(0, 1, 3, 6, 10)
        val out = HashMap<String, Int>()
        for (m in cell.findAll(html)) {
            val tag = m.value
            val id = idRe.find(tag)?.groupValues?.get(1)
            val count = tips[id] ?: levelGuess[(levelRe.find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0).coerceIn(0, 4)]
            out[m.groupValues[1]] = count
        }
        return out
    }

    /** Daily activity: the full-year graph when available, otherwise recent public events (~90 days). */
    suspend fun fetchActivity(username: String): Map<String, Int> = withContext(Dispatchers.IO) {
        val year = fetchContributions(username)
        if (year.isNotEmpty()) return@withContext year
        val map = HashMap<String, Int>()
        try {
            for (page in 1..3) {
                val arr = JSONArray(
                    Http.get("https://api.github.com/users/$username/events/public?per_page=100&page=$page")
                )
                if (arr.length() == 0) break
                for (i in 0 until arr.length()) {
                    val day = Days.fromIso(arr.getJSONObject(i).optString("created_at"))
                    if (day.length == 10) map[day] = (map[day] ?: 0) + 1
                }
                if (arr.length() < 100) break
            }
        } catch (_: Exception) {
            // Activity is optional: ignore failures so the main scan still works.
        }
        map
    }

    suspend fun fetchReleases(fullName: String): List<GhRelease> = withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray(Http.get("https://api.github.com/repos/$fullName/releases?per_page=50"))
            val list = mutableListOf<GhRelease>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += GhRelease(
                    name = o.str("name") ?: o.optString("tag_name"),
                    tagName = o.optString("tag_name"),
                    publishedAt = o.optString("published_at").ifBlank { o.optString("created_at") },
                    body = o.str("body"),
                    htmlUrl = o.optString("html_url"),
                    zipUrl = o.str("zipball_url"),
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
            val jsonStr = try {
                Http.get("https://api.github.com/repos/$fullName/contributors?per_page=50")
            } catch (_: Exception) {
                Http.get("https://api.github.com/repos/$fullName/collaborators?per_page=50")
            }
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += GhCollaborator(
                    login = o.optString("login"),
                    avatarUrl = o.str("avatar_url"),
                    htmlUrl = o.optString("html_url"),
                    contributions = o.optInt("contributions"),
                )
            }
        } catch (_: Exception) { }
        list
    }
}

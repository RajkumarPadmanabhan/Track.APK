package com.trackme.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.app.data.ApiException
import com.trackme.app.data.GhRepo
import com.trackme.app.data.GhUser
import com.trackme.app.data.GitHubApi
import com.trackme.app.data.GitLabApi
import com.trackme.app.data.ParsedProfile
import com.trackme.app.data.Platform
import com.trackme.app.data.PublicProfile
import com.trackme.app.data.SocialApi
import com.trackme.app.data.SortMode
import com.trackme.app.data.Days
import com.trackme.app.data.Store
import com.trackme.app.data.Sync
import com.trackme.app.data.TrackedAccount
import com.trackme.app.data.UserSession
import com.trackme.app.data.detectPlatform
import com.trackme.app.data.mergeWith
import com.trackme.app.data.nameFromEmail
import com.trackme.app.data.newAccount
import com.trackme.app.data.NewsDataRepository
import com.trackme.app.data.UserNewsPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Screen { HOME, PROFILE, DETAIL, LINKED_DEVICES, QR_SCANNER }

class TrackViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("trackme", Context.MODE_PRIVATE)

    // ---------- Session + tracked tags ----------
    var session by mutableStateOf(Store.loadUser(prefs))
        private set
    var accounts by mutableStateOf(session?.let { Store.loadAccounts(prefs, it.email) } ?: emptyList())
        private set
    var screen by mutableStateOf(Screen.HOME)
    var showAdd by mutableStateOf(false)
    var detailId by mutableStateOf<String?>(null)
    // Day (yyyy-MM-dd) on which the home-page git activity card was skipped
    var gitSkipDay by mutableStateOf(prefs.getString("git_skip", "") ?: "")
        private set
    var refreshing by mutableStateOf(false)
        private set
    var refreshNote by mutableStateOf<String?>(null)
        private set

    var platform by mutableStateOf(Platform.GITHUB)
    var input by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    // The user's own YouTube Data API key, stored on this device only.
    var youtubeKey by mutableStateOf(prefs.getString("yt_key", "") ?: "")

    // App Theme Color Customization
    var themeColorName by mutableStateOf(prefs.getString("theme_color", com.trackme.app.ui.ThemeColorOption.CORAL.name) ?: com.trackme.app.ui.ThemeColorOption.CORAL.name)
        private set

    val selectedThemeColor: com.trackme.app.ui.ThemeColorOption
        get() = com.trackme.app.ui.ThemeColorOption.fromName(themeColorName)

    fun updateThemeColor(option: com.trackme.app.ui.ThemeColorOption) {
        themeColorName = option.name
        prefs.edit().putString("theme_color", option.name).apply()
    }

    // Live News State
    private val _newsFeed = MutableStateFlow<List<UserNewsPost>>(emptyList())
    val newsFeed: StateFlow<List<UserNewsPost>> = _newsFeed.asStateFlow()
    private val _isNewsLoading = MutableStateFlow(false)
    val isNewsLoading: StateFlow<Boolean> = _isNewsLoading.asStateFlow()

    fun loadPersonalizedNews(userPlatforms: List<Platform>) {
        viewModelScope.launch {
            _isNewsLoading.value = true
            val news = NewsDataRepository.fetchNewsForPlatforms(userPlatforms)
            _newsFeed.value = news
            _isNewsLoading.value = false
        }
    }

    // Full-scan results (GitHub / GitLab)
    var user by mutableStateOf<GhUser?>(null)
    var repos by mutableStateOf<List<GhRepo>>(emptyList())
    var activity by mutableStateOf<Map<String, Int>>(emptyMap())

    // Link / preview results (Instagram, LinkedIn, X, YouTube)
    var linkProfile by mutableStateOf<ParsedProfile?>(null)
    var preview by mutableStateOf<PublicProfile?>(null)
    var previewNote by mutableStateOf<String?>(null)

    // Repo list filters
    var sort by mutableStateOf(SortMode.BEST)
    var hideForks by mutableStateOf(false)
    var hideArchived by mutableStateOf(false)
    var language by mutableStateOf<String?>(null)
    var query by mutableStateOf("")

    fun updateYoutubeKey(key: String) {
        youtubeKey = key
        prefs.edit().putString("yt_key", key.trim()).apply()
    }

    fun selectPlatform(p: Platform) {
        if (p == platform) return
        platform = p
        resetScan()
    }

    /** Clears the current scan result and the input box. */
    fun resetScan() {
        input = ""
        error = null
        loading = false
        user = null
        repos = emptyList()
        activity = emptyMap()
        linkProfile = null
        preview = null
        previewNote = null
        language = null
        query = ""
    }

    // ---------- Sign in / profile ----------

    /** Returns false when [email] is not a valid address. */
    fun signIn(email: String): Boolean {
        val e = email.trim().lowercase()
        if (!Patterns.EMAIL_ADDRESS.matcher(e).matches()) return false
        val savedName = prefs.getString("name_$e", null)?.takeIf { it.isNotBlank() }
        val savedAvatar = prefs.getString("avatar_$e", null)?.takeIf { it.isNotBlank() }
        val s = UserSession(e, savedName ?: nameFromEmail(e), savedAvatar)
        Store.saveUser(prefs, s)
        session = s
        accounts = Store.loadAccounts(prefs, e)
        screen = Screen.HOME
        refreshNote = null
        return true
    }

    fun signOut() {
        Store.saveUser(prefs, null)
        session = null
        accounts = emptyList()
        screen = Screen.HOME
        showAdd = false
        detailId = null
        resetScan()
    }

    fun updateName(name: String) {
        val s = session ?: return
        val clean = name.trim().ifBlank { return }
        val next = s.copy(name = clean)
        Store.saveUser(prefs, next)
        session = next
    }

    fun updateProfile(name: String, avatarUrl: String?) {
        val s = session ?: return
        val cleanName = name.trim().ifBlank { s.name }
        val cleanAvatar = avatarUrl?.trim()?.ifBlank { null }
        val next = s.copy(name = cleanName, avatarUrl = cleanAvatar)
        Store.saveUser(prefs, next)
        session = next
    }

    // ---------- Tags (scanned accounts shown on the home page) ----------

    private fun persist() {
        session?.let { Store.saveAccounts(prefs, it.email, accounts) }
    }

    fun skipGitToday() {
        gitSkipDay = Days.key()
        prefs.edit().putString("git_skip", gitSkipDay).apply()
    }

    fun openAdd() {
        resetScan()
        showAdd = true
    }

    fun closeAdd() {
        showAdd = false
        resetScan()
    }

    /** The result of the current scan as a home-page tag, or null when nothing was scanned yet. */
    fun currentDraft(): TrackedAccount? {
        val p = platform
        val u = user
        if (u != null && p.fullScan) {
            return newAccount(p, ParsedProfile(u.login, u.htmlUrl), Sync.fromGit(u, repos, activity))
        }
        val lp = linkProfile ?: return null
        val snap = preview?.let { Sync.fromPreview(it) } ?: Sync.fromLink(lp)
        return newAccount(p, lp, snap)
    }

    fun isTracked(draft: TrackedAccount) = accounts.any { it.id == draft.id }

    fun addDraft(): Boolean {
        val draft = currentDraft() ?: return false
        val existing = accounts.firstOrNull { it.id == draft.id }
        accounts = if (existing != null) {
            accounts.map { if (it.id == draft.id) it.mergeWith(draft) else it }
        } else {
            accounts + draft
        }
        accounts = accounts.distinctBy { it.id }
        persist()
        closeAdd()
        return true
    }

    fun removeAccount(id: String) {
        accounts = accounts.filter { it.id != id }
        persist()
    }

    fun setManualCount(id: String, value: Long) {
        val today = Days.key()
        accounts = accounts.map {
            if (it.id == id) it.copy(count = value, history = it.history + (today to value)) else it
        }
        persist()
    }

    fun openAccount(id: String) {
        val acc = accounts.firstOrNull { it.id == id } ?: return
        detailId = id
        screen = Screen.DETAIL
        platform = acc.platform
        resetScan()
        input = acc.url
        scan()
    }

    fun closeDetail() {
        screen = Screen.HOME
        detailId = null
        resetScan()
    }

    /** Re-reads every saved tag and stores today's numbers. Skips if everything is fresh. */
    fun refreshAccounts(force: Boolean = false) {
        val list = accounts
        if (refreshing || list.isEmpty()) return
        val now = System.currentTimeMillis()
        val stale = list.any { it.platform != Platform.X && now - it.syncedAt > 10 * 60_000L }
        if (!force && !stale) return

        refreshing = true
        refreshNote = null
        val key = youtubeKey.trim()
        viewModelScope.launch {
            try {
                val fresh = HashMap<String, TrackedAccount>()
                var note: String? = null
                for (acc in list) {
                    try {
                        val snap = Sync.fetch(acc, key) ?: continue
                        fresh[acc.id] = newAccount(acc.platform, ParsedProfile(acc.handle, acc.url), snap)
                    } catch (e: ApiException) {
                        if (note == null) note = "${acc.platform.label}: ${e.message}"
                    } catch (e: Exception) {
                        if (note == null) note = "Couldn't reach ${acc.platform.label}. Showing saved numbers."
                    }
                }
                accounts = accounts.map { cur -> fresh[cur.id]?.let { cur.mergeWith(it) } ?: cur }
                refreshNote = note
                persist()
            } finally {
                refreshing = false
            }
        }
    }

    /** Used when a link is shared into the app from elsewhere. */
    fun scanText(text: String) {
        detectPlatform(text)?.let { selectPlatform(it) }
        input = text
        scan()
    }

    fun scan() {
        val parsed = platform.parse(input)
        if (parsed == null) {
            error = "Please paste a valid ${platform.label} profile link (e.g. ${platform.example})."
            return
        }
        error = null
        preview = null
        previewNote = null
        val p = platform

        if (!p.fullScan) {
            user = null
            repos = emptyList()
            activity = emptyMap()
            linkProfile = parsed
            if (p.hasPreview) loadPreview(p, parsed)
            return
        }

        linkProfile = null
        loading = true
        viewModelScope.launch {
            try {
                when (p) {
                    Platform.GITHUB -> {
                        val u = GitHubApi.fetchUser(parsed.handle)
                        val r = GitHubApi.fetchRepos(parsed.handle)
                        val a = GitHubApi.fetchActivity(parsed.handle)
                        show(p, u, r, a)
                    }
                    Platform.GITLAB -> {
                        val (u, id) = GitLabApi.fetchUser(parsed.handle)
                        val r = GitLabApi.fetchRepos(id)
                        val a = GitLabApi.fetchActivity(id, parsed.handle)
                        show(p, u.copy(publicRepos = r.size), r, a)
                    }
                    else -> Unit
                }
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Exception) {
                error = "Couldn't reach ${p.label}. Check your internet connection."
            } finally {
                loading = false
            }
        }
    }

    /** Tries to read public profile details; on any failure the link card is shown with a note. */
    private fun loadPreview(p: Platform, parsed: ParsedProfile) {
        if (p == Platform.YOUTUBE && youtubeKey.isBlank()) {
            previewNote = "Add your free YouTube API key above to see channel stats."
            return
        }
        loading = true
        viewModelScope.launch {
            try {
                val result = if (p == Platform.YOUTUBE) {
                    SocialApi.fetchYouTube(parsed, youtubeKey.trim())
                } else {
                    SocialApi.fetchPreview(p, parsed)
                }
                if (platform == p) preview = result
            } catch (e: ApiException) {
                if (platform == p) previewNote = e.message
            } catch (e: Exception) {
                if (platform == p) {
                    previewNote = "Couldn't read the ${p.label} profile. Showing the profile link instead."
                }
            } finally {
                loading = false
            }
        }
    }

    private fun show(p: Platform, u: GhUser, r: List<GhRepo>, a: Map<String, Int>) {
        if (platform != p) return // user switched platform while loading
        user = u
        repos = r
        activity = a
        language = null
        query = ""
    }

    /** Repos after filtering and auto-arranging. */
    fun visibleRepos(): List<GhRepo> {
        var list = repos
        if (hideForks) list = list.filter { !it.isFork }
        if (hideArchived) list = list.filter { !it.archived }
        language?.let { lang -> list = list.filter { it.language == lang } }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) || (it.description ?: "").lowercase().contains(q)
            }
        }
        return when (sort) {
            // Auto-arrange: originals first, then stars, then most recent push
            SortMode.BEST -> list.sortedWith(
                compareBy<GhRepo> { it.isFork }
                    .thenByDescending { it.stars }
                    .thenByDescending { it.pushedAt }
            )
            SortMode.STARS -> list.sortedByDescending { it.stars }
            SortMode.UPDATED -> list.sortedByDescending { it.pushedAt }
            SortMode.FORKS -> list.sortedByDescending { it.forks }
            SortMode.SIZE -> list.sortedByDescending { it.sizeKb }
            SortMode.NAME -> list.sortedBy { it.name.lowercase() }
        }
    }
}

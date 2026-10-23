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
import com.trackme.app.data.PdfPortfolio
import com.trackme.app.data.PdfReadException
import com.trackme.app.data.PdfPortfolioReader
import com.trackme.app.data.UserNewsPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Screen { HOME, PROFILE, DETAIL, LINKED_DEVICES, NEWS }

class TrackViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("trackme", Context.MODE_PRIVATE)

    // Live real-time ticker updating every second
    var liveTicker by mutableStateOf(System.currentTimeMillis())
        private set

    init {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                liveTicker = System.currentTimeMillis()
            }
        }
    }

    // ---------- Session + tracked tags ----------
    var session by mutableStateOf(Store.loadUser(prefs))
        private set
    var accounts by mutableStateOf(session?.let { Store.loadAccounts(prefs, it.email) } ?: emptyList())
        private set
    var screen by mutableStateOf(Screen.HOME)
    var showAdd by mutableStateOf(false)
    var detailId by mutableStateOf<String?>(null)
    // Contribution count when the home-page git activity card was skipped (-1 if not skipped)
    var gitSkippedContributions by mutableStateOf(prefs.getInt("git_skipped_contributions", -1))
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

    fun loadLiveNews() {
        viewModelScope.launch {
            _isNewsLoading.value = true
            val seen = LinkedHashMap<String, UserNewsPost>()
            try {
                NewsDataRepository.liveFlow().collect { batch ->
                    for (post in batch) {
                        val key = post.sourceUrl.ifBlank { post.id }.lowercase()
                        if (key.isNotBlank()) seen.putIfAbsent(key, post)
                    }
                    if (seen.isNotEmpty()) _newsFeed.value = seen.values.toList()
                }
                _newsFeed.value = seen.values.toList()
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (seen.isNotEmpty()) _newsFeed.value = seen.values.toList()
            } finally {
                _isNewsLoading.value = false
            }
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

    // Portfolio built from an uploaded PDF (for example a resume).
    var pdfPortfolio by mutableStateOf<PdfPortfolio?>(null)
        private set
    var pdfScanning by mutableStateOf(false)
        private set

    // Repo list filters
    var sort by mutableStateOf(SortMode.BEST)
    var hideForks by mutableStateOf(false)
    var hideArchived by mutableStateOf(false)
    var language by mutableStateOf<String?>(null)
    var query by mutableStateOf("")

    // Repo releases & collaborators state
    var releasesMap by mutableStateOf<Map<String, List<com.trackme.app.data.GhRelease>>>(emptyMap())
        private set
    var collaboratorsMap by mutableStateOf<Map<String, List<com.trackme.app.data.GhCollaborator>>>(emptyMap())
        private set
    var loadingReleasesForRepo by mutableStateOf<String?>(null)
        private set
    var loadingCollaboratorsForRepo by mutableStateOf<String?>(null)
        private set

    // App Update State
    var currentAppVersion by mutableStateOf("1.2.4")
        private set
    var isCheckingAppUpdate by mutableStateOf(false)
        private set
    var appUpdateMessage by mutableStateOf<String?>(null)
    var appUpdateDownloadUrl by mutableStateOf<String?>(null)

    fun checkAppUpdate() {
        if (isCheckingAppUpdate) return
        isCheckingAppUpdate = true
        appUpdateMessage = null
        appUpdateDownloadUrl = null
        viewModelScope.launch {
            try {
                val jsonStr = try {
                    com.trackme.app.data.Http.get("https://api.github.com/repos/RajkumarPadmanabhan/Track.APK/releases/latest")
                } catch (_: Exception) {
                    com.trackme.app.data.Http.get("https://api.github.com/repos/rrajkumarpadmanabhan/Trackme-v2/releases/latest")
                }
                val o = org.json.JSONObject(jsonStr)
                val tagName = o.optString("tag_name").ifBlank { o.optString("name") }
                val downloadUrl = o.optString("html_url")
                val cleanTag = tagName.trimStart('v', 'V').trim()

                if (cleanTag.isNotBlank() && cleanTag != currentAppVersion && isNewerVersion(cleanTag, currentAppVersion)) {
                    currentAppVersion = cleanTag
                    appUpdateMessage = "New update available: Version $cleanTag!"
                    appUpdateDownloadUrl = downloadUrl.ifBlank { "https://github.com/RajkumarPadmanabhan/Track.APK/releases" }
                } else {
                    appUpdateMessage = "No new updates available. You are using the latest version (v$currentAppVersion)."
                }
            } catch (e: Exception) {
                appUpdateMessage = "No new updates available. You are on the latest version (v$currentAppVersion)."
            } finally {
                isCheckingAppUpdate = false
            }
        }
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        return try {
            val rParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
            val cParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(rParts.size, cParts.size)) {
                val r = rParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            false
        } catch (_: Exception) {
            remote != current
        }
    }

    fun loadReleasesForRepo(fullName: String) {
        if (releasesMap.containsKey(fullName) || loadingReleasesForRepo == fullName) return
        loadingReleasesForRepo = fullName
        viewModelScope.launch {
            try {
                val list = if (platform == Platform.GITLAB) {
                    GitLabApi.fetchReleases(fullName)
                } else {
                    GitHubApi.fetchReleases(fullName)
                }
                releasesMap = releasesMap + (fullName to list)
            } finally {
                if (loadingReleasesForRepo == fullName) loadingReleasesForRepo = null
            }
        }
    }

    fun loadCollaboratorsForRepo(fullName: String) {
        if (collaboratorsMap.containsKey(fullName) || loadingCollaboratorsForRepo == fullName) return
        loadingCollaboratorsForRepo = fullName
        viewModelScope.launch {
            try {
                val list = if (platform == Platform.GITLAB) {
                    GitLabApi.fetchCollaborators(fullName)
                } else {
                    GitHubApi.fetchCollaborators(fullName)
                }
                collaboratorsMap = collaboratorsMap + (fullName to list)
            } finally {
                if (loadingCollaboratorsForRepo == fullName) loadingCollaboratorsForRepo = null
            }
        }
    }

    // Repo actions & pages state
    var actionsMap by mutableStateOf<Map<String, List<com.trackme.app.data.GhActionRun>>>(emptyMap())
        private set
    var pagesMap by mutableStateOf<Map<String, String?>>(emptyMap())
        private set
    var loadingActionsForRepo by mutableStateOf<String?>(null)
        private set

    fun loadActionsForRepo(fullName: String) {
        if (actionsMap.containsKey(fullName) || loadingActionsForRepo == fullName) return
        loadingActionsForRepo = fullName
        viewModelScope.launch {
            try {
                val list = if (platform == Platform.GITLAB) {
                    GitLabApi.fetchActions(fullName)
                } else {
                    GitHubApi.fetchActions(fullName)
                }
                actionsMap = actionsMap + (fullName to list)
            } finally {
                if (loadingActionsForRepo == fullName) loadingActionsForRepo = null
            }
        }
    }

    fun loadPagesForRepo(fullName: String) {
        if (pagesMap.containsKey(fullName)) return
        viewModelScope.launch {
            val url = if (platform == Platform.GITLAB) {
                GitLabApi.fetchPagesUrl(fullName)
            } else {
                GitHubApi.fetchPagesUrl(fullName)
            }
            pagesMap = pagesMap + (fullName to url)
        }
    }

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
        pdfPortfolio = null
        pdfScanning = false
        language = null
        query = ""
    }

    /** Reads every page of the picked PDF and turns its headings into a portfolio. */
    fun onPdfSelected(context: Context, uri: android.net.Uri) {
        if (pdfScanning) return
        pdfScanning = true
        loading = true
        error = null
        previewNote = null
        viewModelScope.launch {
            try {
                val portfolio = PdfPortfolioReader.read(context, uri)
                platform = Platform.PORTFOLIO
                pdfPortfolio = portfolio
                val handle = portfolio.name.lowercase()
                    .replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    .ifBlank { "portfolio" }
                val parsed = ParsedProfile(handle, "pdf:" + portfolio.fileName)
                linkProfile = parsed
                preview = PublicProfile(
                    name = portfolio.name,
                    handle = "@$handle",
                    subtitle = portfolio.headline,
                    description = portfolio.summary,
                    imageUrl = null,
                    stats = listOf(
                        "Sections" to portfolio.sections.size.toString(),
                        "Pages" to portfolio.pageCount.toString(),
                    ),
                    portfolio = portfolio,
                )
                checkCrossAccountDuplicate()
            } catch (e: PdfReadException) {
                error = e.message
            } catch (_: Throwable) {
                error = "Couldn't read this PDF. Pick the original file and try again."
            } finally {
                pdfScanning = false
                loading = false
            }
        }
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

    fun signInWithGoogleUser(user: com.trackme.app.auth.GoogleUserData): Boolean {
        val e = user.email.trim().lowercase()
        val name = user.displayName?.ifBlank { null } ?: nameFromEmail(e)
        val avatar = user.profilePictureUri?.ifBlank { null }
        val savedName = prefs.getString("name_$e", null)?.takeIf { it.isNotBlank() } ?: name
        val savedAvatar = prefs.getString("avatar_$e", null)?.takeIf { it.isNotBlank() } ?: avatar
        val s = UserSession(e, savedName, savedAvatar)
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

    fun skipGit(totalContributions: Int) {
        gitSkippedContributions = totalContributions
        prefs.edit().putInt("git_skipped_contributions", totalContributions).apply()
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
        pdfPortfolio?.let { portfolio ->
            val pub = preview ?: PublicProfile(
                name = portfolio.name,
                handle = portfolio.name,
                subtitle = portfolio.headline,
                description = portfolio.summary,
                imageUrl = null,
                stats = emptyList(),
                portfolio = portfolio,
            )
            val handle = portfolio.name.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-").trim('-')
                .ifBlank { "portfolio" }
            return newAccount(
                Platform.PORTFOLIO,
                ParsedProfile(handle, "pdf:" + portfolio.fileName),
                Sync.fromPreview(pub),
            )
        }
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

    fun checkCrossAccountDuplicate(): Boolean {
        val draft = currentDraft() ?: return false
        val email = session?.email ?: ""
        if (Store.isTrackedByAnotherAccount(prefs, email, draft)) {
            error = "added to another account remove from there to add here"
            return true
        }
        return false
    }

    fun addDraft(linkedEmail: String? = null): Boolean {
        if (checkCrossAccountDuplicate()) return false
        val base = currentDraft() ?: return false
        val mail = linkedEmail?.trim()?.lowercase()?.takeIf { it.contains("@") }
        val draft = if (mail == null) base else base.copy(linkedEmail = mail)
        val existing = accounts.firstOrNull { it.id == draft.id }
        accounts = if (existing != null) {
            accounts.map { if (it.id == draft.id) it.mergeWith(draft) else it }
        } else {
            accounts + draft
        }
        accounts = accounts.distinctBy { it.id }
        if (draft.platform.fullScan) {
            gitSkippedContributions = -1
            prefs.edit().remove("git_skipped_contributions").apply()
        }
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

    fun reorderGroup(orderedIds: List<String>) {
        if (orderedIds.size < 2) return
        val byId = accounts.associateBy { it.id }
        val moving = orderedIds.mapNotNull { byId[it] }
        if (moving.size < 2) return
        val movingIds = moving.map { it.id }.toSet()
        val next = ArrayList<TrackedAccount>(accounts.size)
        var placed = false
        for (acc in accounts) {
            if (acc.id in movingIds) {
                if (!placed) {
                    next.addAll(moving)
                    placed = true
                }
            } else {
                next.add(acc)
            }
        }
        accounts = next
        persist()
    }

    fun toggleAccountVisibility(id: String) {
        accounts = accounts.map {
            if (it.id == id) it.copy(isHidden = !it.isHidden) else it
        }
        persist()
    }

    fun openAccount(id: String) {
        val acc = accounts.firstOrNull { it.id == id } ?: return
        detailId = id
        screen = Screen.DETAIL
        platform = acc.platform
        resetScan()
        if (acc.portfolioSections.isNotEmpty()) {
            // The portfolio is saved on this phone. Show it as saved, with no web scan.
            val portfolio = PdfPortfolio(
                fileName = acc.portfolioFileName ?: "Resume.pdf",
                pageCount = acc.portfolioPageCount,
                name = acc.name,
                headline = acc.subtitle,
                email = acc.portfolioEmail,
                phone = acc.portfolioPhone,
                links = acc.portfolioLinks,
                summary = acc.portfolioSections.firstOrNull { it.category == "Profile" }
                    ?.items?.joinToString(" ")?.take(600),
                sections = acc.portfolioSections,
                skills = acc.portfolioSections.firstOrNull { it.category == "Skills" }
                    ?.items?.flatMap { it.split(Regex("[,|/•]")) }
                    ?.map { it.trim() }?.filter { it.isNotBlank() }?.take(30)
                    .orEmpty(),
                usedOcr = false,
            )
            pdfPortfolio = portfolio
            linkProfile = ParsedProfile(acc.handle, acc.url)
            preview = PublicProfile(
                name = acc.name,
                handle = "@" + acc.handle,
                subtitle = acc.subtitle,
                description = portfolio.summary,
                imageUrl = null,
                stats = listOf(
                    "Sections" to acc.portfolioSections.size.toString(),
                    "Pages" to acc.portfolioPageCount.toString(),
                ),
                portfolio = portfolio,
            )
            input = acc.url
            return
        }
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
        val stale = list.any {
            val missingSocialCount = it.count == null && (
                it.platform == Platform.LINKEDIN || it.platform == Platform.FACEBOOK || it.platform == Platform.INSTAGRAM
                )
            missingSocialCount || (it.platform != Platform.X && now - it.syncedAt > 10 * 60_000L)
        }
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
                        if (note == null && e.message?.contains("Rate limit", ignoreCase = true) != true) {
                            note = "${acc.platform.label}: ${e.message}"
                        }
                    } catch (_: Throwable) {
                        // Keep saved numbers silently. A bad page must not close the app.
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
            if (checkCrossAccountDuplicate()) return
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
            } catch (_: Throwable) {
                if (platform == p) {
                    previewNote = "Couldn't read the ${p.label} profile. Showing the profile link instead."
                }
            } finally {
                loading = false
                checkCrossAccountDuplicate()
            }
        }
    }

    private fun show(p: Platform, u: GhUser, r: List<GhRepo>, a: Map<String, Int>) {
        if (platform != p) return // user switched platform while loading
        user = u
        repos = r
        activity = a
        gitSkippedContributions = -1
        prefs.edit().remove("git_skipped_contributions").apply()
        language = null
        query = ""
        checkCrossAccountDuplicate()
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

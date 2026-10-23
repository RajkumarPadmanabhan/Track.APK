package com.trackme.app

import android.app.Application
import com.trackme.app.BuildConfig
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
import com.trackme.app.data.combinedActivity
import com.trackme.app.data.Store
import com.trackme.app.data.Sync
import com.trackme.app.data.TrackedAccount
import com.trackme.app.data.siteKey
import com.trackme.app.data.UserSession
import com.trackme.app.data.detectPlatform
import com.trackme.app.data.mergeWith
import com.trackme.app.data.nameFromEmail
import com.trackme.app.data.nameFromLink
import com.trackme.app.data.newAccount
import com.trackme.app.data.NewsDataRepository
import com.trackme.app.data.UploadedFile
import com.trackme.app.data.Backup
import com.trackme.app.data.BadgeBook
import com.trackme.app.data.EarnedBadge
import com.trackme.app.data.DesktopLink
import com.trackme.app.data.compactCount
import com.trackme.app.data.formatBytes
import com.trackme.app.data.UploadedFileStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.trackme.app.data.PdfPortfolio
import com.trackme.app.data.FilePreviewer
import com.trackme.app.data.FileStoreException
import com.trackme.app.data.NewsTopics
import com.trackme.app.data.UserNewsPost
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Screen { HOME, PROFILE, DETAIL, LINKED_DEVICES, NEWS, FILES }

class TrackViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("trackme", Context.MODE_PRIVATE)

    // Live real-time ticker updating every second
    var liveTicker by mutableStateOf(System.currentTimeMillis())
        private set

    // ---------- Session + tracked tags ----------
    var session by mutableStateOf(Store.loadUser(prefs))
        private set
    var accounts by mutableStateOf(session?.let { Store.loadAccounts(prefs, it.email) } ?: emptyList())
        private set
    var uploadedFiles by mutableStateOf(session?.let { UploadedFileStore.load(prefs, it.email) } ?: emptyList())
        private set

    val totalUploadedBytes: Long
        get() = uploadedFiles.sumOf { it.originalSize }
    var earnedBadges by mutableStateOf<List<EarnedBadge>>(emptyList())
        private set
    var pinnedBadgeIds by mutableStateOf<List<String>>(emptyList())
        private set
    var verified by mutableStateOf(false)
        private set

    init {
        loadBadgeState()
        refreshBadges()
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                liveTicker = System.currentTimeMillis()
            }
        }
    }

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
    // The scan box edits youtubeKeyDraft. Clearing that box does not erase a saved key.
    var youtubeKey by mutableStateOf(prefs.getString("yt_key", "") ?: "")
        private set
    var youtubeKeyDraft by mutableStateOf("")
    var editingYoutubeKey by mutableStateOf(false)

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

    var newsTopicIds by mutableStateOf(
        prefs.getString("news_topics", null)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { id -> NewsTopics.all.any { it.id == id } }
            ?.take(NewsTopics.MAX)
            ?: emptyList()
    )
        private set

    private var newsJob: Job? = null
    private var newsBusy = false
    private var newsGen = 0

    fun toggleNewsTopic(id: String) {
        if (NewsTopics.all.none { it.id == id }) return
        val next = newsTopicIds.toMutableList()
        if (id in next) next.remove(id)
        else if (next.size < NewsTopics.MAX) next.add(id)
        else return
        newsTopicIds = next
        prefs.edit().putString("news_topics", next.joinToString(",")).apply()
        loadLiveNews(keepCurrent = false)
    }

    fun loadLiveNews(keepCurrent: Boolean = false) {
        val topics = NewsTopics.selected(newsTopicIds)
        val allowed = topics.map { it.label }.toSet()
        if (topics.isEmpty()) {
            newsJob?.cancel()
            newsBusy = false
            _newsFeed.value = emptyList()
            _isNewsLoading.value = false
            return
        }
        if (!keepCurrent) {
            newsJob?.cancel()
            newsBusy = false
            _newsFeed.value = emptyList()
            _isNewsLoading.value = true
        } else {
            _newsFeed.value = NewsDataRepository.arrange(_newsFeed.value.filter { it.categoryTag in allowed })
            if (newsBusy) return
        }
        val gen = ++newsGen
        newsJob = viewModelScope.launch {
            newsBusy = true
            val seen = LinkedHashMap<String, UserNewsPost>()
            if (keepCurrent) {
                for (post in _newsFeed.value) {
                    val key = post.sourceUrl.ifBlank { post.id }.lowercase()
                    if (key.isNotBlank()) seen[key] = post
                }
            }
            try {
                NewsDataRepository.liveFlow(topics).collect { batch ->
                    for (post in batch) {
                        val clean = NewsDataRepository.cleanPost(post)
                        val key = clean.sourceUrl.ifBlank { clean.id }.lowercase()
                        if (key.isNotBlank()) seen[key] = clean
                    }
                    if (seen.isNotEmpty()) _newsFeed.value = NewsDataRepository.arrange(seen.values.toList())
                }
                if (seen.isNotEmpty()) _newsFeed.value = NewsDataRepository.arrange(seen.values.toList())
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (seen.isNotEmpty()) _newsFeed.value = NewsDataRepository.arrange(seen.values.toList())
            } finally {
                if (gen == newsGen) {
                    newsBusy = false
                    _isNewsLoading.value = false
                }
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
    var currentAppVersion by mutableStateOf(BuildConfig.VERSION_NAME)
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

    fun saveYoutubeKey(key: String = youtubeKeyDraft): Boolean {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return youtubeKey.isNotBlank()
        youtubeKey = trimmed
        youtubeKeyDraft = ""
        editingYoutubeKey = false
        prefs.edit().putString("yt_key", trimmed).apply()
        return true
    }

    /** Drops the saved key. Used only after the last YouTube channel is removed. */
    fun clearYoutubeKey() {
        youtubeKey = ""
        youtubeKeyDraft = ""
        editingYoutubeKey = false
        prefs.edit().remove("yt_key").apply()
    }

    /** Pulls an API key out of a pasted channel link so the link box stays a link. */
    fun onYoutubeInput(raw: String) {
        val match = Regex("""AIza[0-9A-Za-z_-]{20,}""").find(raw)
        if (match != null) {
            saveYoutubeKey(match.value)
            input = raw.removeRange(match.range).replace(Regex("\\s+"), " ").trim()
        } else {
            input = raw
        }
        suggestAppName(input)
    }

    fun onOtherInput(raw: String) {
        input = raw
        suggestAppName(raw)
        val parsed = Platform.PORTFOLIO.parse(raw)
        if (parsed == null) {
            linkProfile = null
            preview = null
            previewNote = null
            return
        }
        linkProfile = parsed
        val host = parsed.url.substringAfter("://").removePrefix("www.").substringBefore('/').substringBefore('?')
        val name = host.substringBefore('.').replaceFirstChar { it.uppercase() }.ifBlank { "Website" }
        preview = PublicProfile(
            name = name,
            handle = host.ifBlank { parsed.handle },
            subtitle = parsed.url,
            description = null,
            imageUrl = null,
            stats = emptyList(),
        )
        previewNote = null
        error = null
    }

    private fun youtubeKeyForScan(): String {
        val typed = youtubeKeyDraft.trim()
        if (typed.isNotBlank()) saveYoutubeKey(typed)
        return youtubeKey.trim()
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
        appName = ""
        appNameTouched = false
        makeAsApp = false
        youtubeKeyDraft = ""
        editingYoutubeKey = false
    }

    var makeAsApp by mutableStateOf(false)
    var appName by mutableStateOf("")
    private var appNameTouched = false

    fun updateMakeAsApp(on: Boolean) {
        makeAsApp = on
        if (on && !appNameTouched) appName = nameFromLink(input)
    }

    fun suggestAppName(link: String) {
        if (!appNameTouched) appName = nameFromLink(link)
    }

    fun onAppNameChange(value: String) {
        appNameTouched = true
        appName = value.take(32)
    }

    /** Stores the picked file compressed. It is not opened, scanned into a portfolio, or run. */
    fun onFileSelected(context: Context, uri: android.net.Uri) {
        if (pdfScanning) return
        pdfScanning = true
        error = null
        viewModelScope.launch {
            try {
                val record = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    UploadedFileStore.import(context.applicationContext, uri)
                }
                uploadedFiles = listOf(record) + uploadedFiles.filter { it.id != record.id }
                session?.let { UploadedFileStore.save(prefs, it.email, uploadedFiles) }
                refreshBadges()
                android.widget.Toast.makeText(
                    context,
                    "File uploaded successfully",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            } catch (e: FileStoreException) {
                error = e.message
            } catch (_: Throwable) {
                error = "Couldn't save that file."
            } finally {
                pdfScanning = false
            }
        }
    }

    fun prepareFilePreview(context: Context, file: UploadedFile, onDone: (FilePreviewer.Result) -> Unit) {
        viewModelScope.launch {
            val result = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    FilePreviewer.prepare(context.applicationContext, file)
                }
            } catch (_: Throwable) {
                FilePreviewer.Result(
                    FilePreviewer.Kind.BLOCKED,
                    note = "This file is saved. It was not opened, so nothing in it can run.",
                )
            }
            onDone(result)
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
        uploadedFiles = UploadedFileStore.load(prefs, e)
        loadBadgeState()
        refreshBadges()
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
        uploadedFiles = UploadedFileStore.load(prefs, e)
        loadBadgeState()
        refreshBadges()
        screen = Screen.HOME
        refreshNote = null
        return true
    }

    private fun badgeKey(email: String, name: String) = "${name}_$email"

    private fun loadBadgeState() {
        val email = session?.email
        if (email == null) {
            earnedBadges = emptyList()
            pinnedBadgeIds = emptyList()
            verified = false
            return
        }
        earnedBadges = BadgeBook.fromJson(prefs.getString(badgeKey(email, "badges"), null))
        pinnedBadgeIds = prefs.getString(badgeKey(email, "pinned"), "")
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        verified = prefs.getBoolean(badgeKey(email, "verified"), false)
    }

    private fun rememberAppDay(email: String): Int {
        val key = badgeKey(email, "app_days")
        val days = prefs.getStringSet(key, emptySet())?.toHashSet() ?: HashSet()
        val today = Days.key()
        if (days.add(today)) prefs.edit().putStringSet(key, days).apply()
        return days.size
    }

    fun refreshBadges() {
        val email = session?.email ?: return
        val next = BadgeBook.evaluate(
            scans = accounts.count { !it.asApp },
            uploads = uploadedFiles.size,
            activity = combinedActivity(accounts).values.sum().toLong() +
                accounts.filter { !it.isHidden }.sumOf { it.count ?: 0L },
            bytes = totalUploadedBytes,
            activeDays = rememberAppDay(email),
            existing = earnedBadges,
        )
        if (next.map { it.id to it.earnedAt } != earnedBadges.map { it.id to it.earnedAt }) {
            earnedBadges = next
            prefs.edit().putString(badgeKey(email, "badges"), BadgeBook.toJson(next)).apply()
            val live = next.map { it.id }.toSet()
            if (pinnedBadgeIds.any { it !in live }) {
                pinnedBadgeIds = pinnedBadgeIds.filter { it in live }
                prefs.edit().putString(badgeKey(email, "pinned"), pinnedBadgeIds.joinToString(",")).apply()
            }
        }
    }

    fun togglePinnedBadge(id: String) {
        val email = session?.email ?: return
        if (earnedBadges.none { it.id == id }) return
        pinnedBadgeIds = if (id in pinnedBadgeIds) pinnedBadgeIds.filter { it != id } else pinnedBadgeIds + id
        prefs.edit().putString(badgeKey(email, "pinned"), pinnedBadgeIds.joinToString(",")).apply()
    }

    fun redeemCode(code: String): Boolean {
        val email = session?.email ?: return false
        if (code.trim() != "SPECIALDEALX") return false
        verified = true
        prefs.edit().putBoolean(badgeKey(email, "verified"), true).apply()
        return true
    }

    var walkthroughDone by mutableStateOf(prefs.getBoolean("walkthrough_done", false))
        private set

    fun finishWalkthrough() {
        walkthroughDone = true
        prefs.edit().putBoolean("walkthrough_done", true).apply()
    }

    fun exportBackup(context: Context, dest: android.net.Uri) {
        val email = session?.email ?: return
        viewModelScope.launch {
            val ok = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(dest)?.use { out ->
                        Backup.write(context.applicationContext, prefs, email, out)
                    } != null
                }
            } catch (_: Throwable) {
                false
            }
            android.widget.Toast.makeText(
                context,
                if (ok) "Backup saved" else "Couldn't save the backup.",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun importBackup(context: Context, uri: android.net.Uri) {
        val email = session?.email ?: return
        viewModelScope.launch {
            val restored = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    Backup.read(context.applicationContext, uri)
                }
            } catch (_: Throwable) {
                null
            }
            if (restored == null) {
                android.widget.Toast.makeText(context, "That file is not a Track backup.", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val topics = restored.newsTopics.split(",")
                .map { it.trim() }
                .filter { id -> NewsTopics.all.any { it.id == id } }
                .take(NewsTopics.MAX)
            prefs.edit()
                .putString("accounts_$email", restored.accountsJson)
                .putString("files_$email", restored.filesJson)
                .putString("news_topics", topics.joinToString(","))
                .apply()
            newsTopicIds = topics
            com.trackme.app.ui.ThemeColorOption.entries
                .firstOrNull { it.name == restored.theme }
                ?.let { updateThemeColor(it) }
            accounts = Store.loadAccounts(prefs, email)
            uploadedFiles = UploadedFileStore.load(prefs, email)
            refreshBadges()
            android.widget.Toast.makeText(context, "Backup restored", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun signOut() {
        desktopNote = null
        stopDesktop()
        Store.saveUser(prefs, null)
        session = null
        accounts = emptyList()
        uploadedFiles = emptyList()
        earnedBadges = emptyList()
        pinnedBadgeIds = emptyList()
        verified = false
        screen = Screen.HOME
        showAdd = false
        detailId = null
        resetScan()
    }

    var desktopUrl by mutableStateOf<String?>(null)
    var desktopCode by mutableStateOf<String?>(null)
    var desktopError by mutableStateOf<String?>(null)
    var desktopNote by mutableStateOf<String?>(null)
        private set
    private var desktopAddJob: Job? = null

    fun startDesktop() {
        val url = DesktopLink.start(object : DesktopLink.Bridge {
            override fun stateJson(): String = desktopState()
            override fun act(type: String, id: String) = desktopAct(type, id)
            override fun file(id: String): File? = desktopFile(id)
            override fun windowsApp(): File? = windowsInstaller()
        })
        desktopUrl = url
        desktopCode = DesktopLink.code
        desktopError = if (url == null) {
            "Connect the phone to Wi-Fi, then open Linked Devices again. A computer cannot reach the phone over mobile data."
        } else {
            null
        }
    }

    fun stopDesktop() {
        DesktopLink.stop()
        desktopUrl = null
        desktopCode = null
    }

    private fun desktopAct(type: String, id: String) {
        val run = Runnable {
            when (type) {
                "home" -> closeDetail()
                "news" -> {
                    screen = Screen.NEWS
                    if (newsFeed.value.isEmpty()) loadLiveNews()
                }
                "files" -> screen = Screen.FILES
                "open" -> if (id.isNotBlank()) openAccount(id)
                "refresh" -> refreshAccounts(true)
                "scan" -> addFromDesktop(id)
                "theme" -> {
                    val option = com.trackme.app.ui.ThemeColorOption.entries.firstOrNull {
                        it.name.equals(id, ignoreCase = true)
                    }
                    if (option != null) updateThemeColor(option)
                }
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) run.run() else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(run)
        }
    }

    private fun desktopFile(id: String): File? {
        val item = uploadedFiles.firstOrNull { it.id == id } ?: return null
        val out = File(getApplication<Application>().cacheDir, "desk-" + item.id + ".pdf")
        return if (UploadedFileStore.writeOriginal(getApplication(), item, out)) out else null
    }

    private fun desktopState(): String {
        val root = JSONObject()
        root.put("name", session?.name ?: "Track")
        root.put("screen", screen.name)
        val cards = JSONArray()
        accounts.filter { !it.isHidden }.forEach { acc ->
            cards.put(JSONObject().apply {
                put("id", acc.id)
                put("platform", acc.platform.label)
                put("name", acc.name)
                put("handle", acc.handle)
                put("count", acc.count?.takeIf { it > 0 }?.let { compactCount(it) } ?: "")
                put("countLabel", if (acc.count != null && acc.count > 0) acc.countLabel else "")
            })
        }
        root.put("accounts", cards)
        val open = accounts.firstOrNull { it.id == detailId }
        if (open != null) {
            val extras = JSONArray()
            open.extras.forEach { (label, value) ->
                if (value.isNotBlank()) extras.put(JSONObject().put("label", label).put("value", value))
            }
            val sections = JSONArray()
            open.portfolioSections.forEach { sec ->
                sections.put(JSONObject().put("title", sec.heading.ifBlank { sec.category }).put("items", JSONArray(sec.items)))
            }
            root.put("detail", JSONObject().apply {
                put("platform", open.platform.label)
                put("name", open.name)
                put("handle", open.handle)
                put("count", open.count?.takeIf { it > 0 }?.let { compactCount(it) } ?: "")
                put("countLabel", if (open.count != null && open.count > 0) open.countLabel else "")
                put("extras", extras)
                put("sections", sections)
            })
        }
        val news = JSONArray()
        newsFeed.value.take(40).forEach { post ->
            news.put(JSONObject().put("title", post.title).put("source", post.sourceName).put("url", post.sourceUrl))
        }
        root.put("news", news)
        val files = JSONArray()
        uploadedFiles.forEach { file ->
            files.put(JSONObject().put("id", file.id).put("name", file.displayName).put("size", formatBytes(file.originalSize)))
        }
        root.put("files", files)
        root.put("alive", true)
        root.put("theme", themeColorName)
        if (!desktopNote.isNullOrBlank()) root.put("note", desktopNote)
        return root.toString()
    }

    /** A link typed on the computer is scanned and saved on this phone. */
    private fun addFromDesktop(raw: String) {
        if (session == null) {
            desktopNote = "Sign in on the phone first"
            return
        }
        val text = raw.trim()
        val found = detectPlatform(text)
        if (found == null) {
            desktopNote = "Paste a profile link this app can read"
            return
        }
        platform = found
        resetScan()
        input = text
        desktopNote = "Reading that link…"
        scan()
        desktopAddJob?.cancel()
        desktopAddJob = viewModelScope.launch {
            var spins = 0
            while (loading && spins < 80) {
                kotlinx.coroutines.delay(250)
                spins++
            }
            if (error != null && currentDraft() == null) {
                desktopNote = error
                return@launch
            }
            val draft = currentDraft()
            if (draft == null) {
                desktopNote = previewNote ?: error ?: "Couldn't read that link"
                return@launch
            }
            val pending = previewNote
            if (addDraft()) desktopNote = pending ?: "Added on the phone"
            else desktopNote = error ?: "Couldn't add that link"
        }
    }

    private fun windowsInstaller(): File? {
        return try {
            val out = File(getApplication<Application>().cacheDir, "TrackWindows.cmd")
            getApplication<Application>().assets.open("TrackWindows.cmd").use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            out.takeIf { it.length() > 0L }
        } catch (_: Exception) {
            null
        }
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
        refreshBadges()
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

    /** A home-screen shortcut. Stores the link and the name only. Nothing is fetched. */
    fun addDummyApp(): Boolean {
        val raw = input.trim()
        if (raw.isBlank()) {
            error = "Paste a link"
            return false
        }
        val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        val label = appName.trim().ifBlank { nameFromLink(url) }.ifBlank { "App" }.take(32)
        val id = "app:" + url.lowercase()
        val shortcut = TrackedAccount(
            id = id,
            platform = detectPlatform(url) ?: platform,
            handle = label,
            url = url,
            name = label,
            avatarUrl = null,
            subtitle = null,
            count = null,
            countLabel = "",
            extras = emptyList(),
            history = emptyMap(),
            activity = emptyMap(),
            addedAt = Days.key(),
            syncedAt = System.currentTimeMillis(),
            asApp = true,
            appName = label,
        )
        accounts = accounts.filter { it.id != id } + shortcut
        persist()
        closeAdd()
        return true
    }

    fun addDraft(linkedEmail: String? = null): Boolean {
        if (checkCrossAccountDuplicate()) return false
        val base = currentDraft() ?: return false
        val mail = linkedEmail?.trim()?.lowercase()?.takeIf { it.contains("@") }
        val named = if (makeAsApp) {
            val label = appName.trim().ifBlank { nameFromLink(base.url).ifBlank { base.name } }.take(32)
            base.copy(asApp = true, appName = label)
        } else {
            base.copy(asApp = false, appName = null)
        }
        val draft = if (mail == null) named else named.copy(linkedEmail = mail)
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

    fun deleteUploadedFile(context: Context, file: UploadedFile) {
        UploadedFileStore.deleteStored(context.applicationContext, file)
        uploadedFiles = uploadedFiles.filter { it.id != file.id }
        session?.let { UploadedFileStore.save(prefs, it.email, uploadedFiles) }
    }

    /** Writes the exact original PDF to the place the user picked, then proves it by hash. */
    fun downloadUploadedFile(context: Context, file: UploadedFile, dest: android.net.Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val ext = file.extension.ifBlank { "bin" }.filter { it.isLetterOrDigit() }.take(8).ifBlank { "bin" }
                    val tmp = java.io.File(context.cacheDir, "download_${file.id}.$ext")
                    val written = UploadedFileStore.writeOriginal(context.applicationContext, file, tmp)
                    if (!written) {
                        false
                    } else {
                        context.contentResolver.openOutputStream(dest)?.use { out ->
                            tmp.inputStream().use { it.copyTo(out) }
                        } != null
                    }
                }
            } catch (_: Throwable) {
                false
            }
            onDone(ok)
        }
    }

    fun removeAccount(id: String) {
        val removed = accounts.firstOrNull { it.id == id }
        accounts = accounts.filter { it.id != id }
        persist()
        val removedYoutube = removed?.platform == Platform.YOUTUBE && removed.asApp == false
        val anyYoutubeLeft = accounts.any { it.platform == Platform.YOUTUBE && !it.asApp }
        if (removedYoutube && !anyYoutubeLeft) clearYoutubeKey()
    }

    fun setManualCount(id: String, value: Long) {
        val today = Days.key()
        accounts = accounts.map {
            if (it.id == id) it.copy(count = value, history = it.history + (today to value)) else it
        }
        persist()
    }

    fun moveSite(siteKey: String, forward: Boolean) {
        val platformAccounts = accounts.filter { !it.asApp }
        val keys = platformAccounts.map { it.siteKey() }.distinct()
        val index = keys.indexOf(siteKey)
        val swapWith = if (forward) index - 1 else index + 1
        if (index < 0 || swapWith !in keys.indices) return
        val nextKeys = keys.toMutableList()
        val moved = nextKeys.removeAt(index)
        nextKeys.add(swapWith, moved)
        val byKey = platformAccounts.groupBy { it.siteKey() }
        val reordered = nextKeys.flatMap { byKey[it].orEmpty() }
        if (reordered.size != platformAccounts.size) return
        val next = ArrayList<TrackedAccount>(accounts.size)
        var cursor = 0
        for (acc in accounts) {
            if (acc.asApp) next.add(acc) else next.add(reordered[cursor++])
        }
        accounts = next
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
        val list = accounts.filter { !it.asApp }
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
        val key = if (p == Platform.YOUTUBE) youtubeKeyForScan() else ""
        if (p == Platform.YOUTUBE && key.isBlank()) {
            previewNote = "Add your YouTube API key once. It is reused for every channel."
            return
        }
        if (p == Platform.PORTFOLIO) {
            val host = parsed.url.substringAfter("://").removePrefix("www.").substringBefore('/').substringBefore('?')
            val name = host.substringBefore('.').replaceFirstChar { it.uppercase() }.ifBlank { "Website" }
            preview = PublicProfile(
                name = name,
                handle = host.ifBlank { parsed.handle },
                subtitle = parsed.url,
                description = null,
                imageUrl = null,
                stats = emptyList(),
            )
            return
        }
        loading = true
        viewModelScope.launch {
            try {
                val result = if (p == Platform.YOUTUBE) {
                    SocialApi.fetchYouTube(parsed, key)
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

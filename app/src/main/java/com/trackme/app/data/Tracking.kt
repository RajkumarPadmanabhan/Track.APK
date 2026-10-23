package com.trackme.app.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** The signed-in person. Everything tracked is stored per email, on this device. */
data class UserSession(val email: String, val name: String, val avatarUrl: String? = null)

/**
 * One scanned "tag" that lives on the home page.
 *  - count      : headline number (followers / subscribers), null when the platform doesn't expose it
 *  - history    : one saved value per day (yyyy-MM-dd, local time) -> used for the daily +/- change
 *  - activity   : git events per day (GitHub / GitLab), used for the heatmap and streaks
 */
data class TrackedAccount(
    val id: String,
    val platform: Platform,
    val handle: String,
    val url: String,
    val name: String,
    val avatarUrl: String?,
    val subtitle: String?,
    val count: Long?,
    val countLabel: String,
    val extras: List<Pair<String, String>>,
    val history: Map<String, Long>,
    val activity: Map<String, Int>,
    val addedAt: String,
    val syncedAt: Long,
    val isHidden: Boolean = false,
)

/** Fresh data read from a platform, before it is merged into a [TrackedAccount]. */
data class Snapshot(
    val name: String,
    val avatarUrl: String?,
    val subtitle: String?,
    val count: Long?,
    val countLabel: String,
    val extras: List<Pair<String, String>>,
    val activity: Map<String, Int>?,
)

data class Streak(val current: Int, val longest: Int, val activeDays: Int, val since: String?)

// ---------- Dates (all "days" are local calendar days, formatted yyyy-MM-dd) ----------

object Days {
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun formatter(tz: TimeZone) =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }

    /** Local calendar day, [offset] days away from today. */
    fun key(offset: Int = 0): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, offset)
        return formatter(TimeZone.getDefault()).format(c.time)
    }

    /** Whole-day number for a yyyy-MM-dd key, so consecutive days differ by exactly 1. */
    fun number(key: String): Long? = try {
        formatter(utc).parse(key)?.time?.div(86_400_000L)
    } catch (e: Exception) {
        null
    }

    /** Converts an API UTC timestamp (2026-10-21T18:45:00Z) into the user's local day. */
    fun fromIso(iso: String): String {
        if (iso.length < 19) return iso.take(10)
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = utc }
            val d = parser.parse(iso.substring(0, 19))
            if (d == null) iso.take(10) else formatter(TimeZone.getDefault()).format(d)
        } catch (e: Exception) {
            iso.take(10)
        }
    }

    fun pretty(key: String, withYear: Boolean = false): String = try {
        val d = formatter(TimeZone.getDefault()).parse(key)
        if (d == null) key else SimpleDateFormat(if (withYear) "MMM d, yyyy" else "MMM d", Locale.getDefault()).format(d)
    } catch (e: Exception) {
        key
    }
}

// ---------- Numbers ----------

/** "1,234" -> 1234, "12.5K" -> 12500, "1.2M" -> 1200000. Returns null for "Hidden", "-", blank. */
fun parseCount(raw: String?): Long? {
    val t = raw?.trim()?.replace(",", "")?.replace(" ", "") ?: return null
    val m = Regex("""^([0-9]*\.?[0-9]+)([KMBkmb]?)$""").find(t) ?: return null
    val n = m.groupValues[1].toDoubleOrNull() ?: return null
    val mult = when (m.groupValues[2].uppercase()) {
        "K" -> 1_000.0
        "M" -> 1_000_000.0
        "B" -> 1_000_000_000.0
        else -> 1.0
    }
    return Math.round(n * mult)
}

/** 842 -> "842", 1000 -> "1.0k", 2450 -> "2.45k", 2400 -> "2.4k", 2_400_000 -> "2.4M". */
fun compactCount(n: Long): String {
    val a = Math.abs(n)
    if (a < 1000) return n.toString()
    if (a < 1_000_000) {
        val v = n / 1000.0
        val s = String.format(Locale.US, "%.2f", v).trimEnd('0')
        val formatted = if (s.endsWith(".")) s + "0" else s
        return formatted + "k"
    }
    val v = n / 1_000_000.0
    val s = String.format(Locale.US, "%.2f", v).trimEnd('0')
    val formatted = if (s.endsWith(".")) s + "0" else s
    return formatted + "M"
}

private fun trimZero(s: String) = if (s.endsWith(".0")) s.dropLast(2) else s

fun nameFromEmail(email: String): String {
    val local = email.substringBefore("@")
    val words = local.replace(Regex("[._+\\-]+"), " ").replace(Regex("[0-9]"), "").trim()
        .split(" ").filter { it.isNotBlank() }
    if (words.isEmpty()) return "Friend"
    return words.joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
}

// ---------- Account helpers ----------

fun accountId(platform: Platform, handle: String) =
    platform.name + ":" + handle.trim().trimStart('@').lowercase()

fun newAccount(platform: Platform, parsed: ParsedProfile, s: Snapshot): TrackedAccount {
    val today = Days.key()
    return TrackedAccount(
        id = accountId(platform, parsed.handle),
        platform = platform,
        handle = parsed.handle.trimStart('@'),
        url = parsed.url,
        name = s.name,
        avatarUrl = s.avatarUrl,
        subtitle = s.subtitle,
        count = s.count,
        countLabel = s.countLabel,
        extras = s.extras,
        history = s.count?.let { mapOf(today to it) } ?: emptyMap(),
        activity = s.activity ?: emptyMap(),
        addedAt = today,
        syncedAt = System.currentTimeMillis(),
        isHidden = false,
    )
}

/** Merges freshly fetched data [f] into this account, keeping everything saved so far. */
fun TrackedAccount.mergeWith(f: TrackedAccount): TrackedAccount {
    val act = HashMap<String, Int>(activity)
    for ((k, v) in f.activity) act[k] = maxOf(act[k] ?: 0, v)
    return copy(
        name = f.name.ifBlank { name },
        avatarUrl = f.avatarUrl ?: avatarUrl,
        subtitle = f.subtitle ?: subtitle,
        count = f.count ?: count,
        countLabel = if (f.count != null) f.countLabel else countLabel,
        extras = if (!platform.fullScan && platform != Platform.YOUTUBE && (f.count != null || f.extras.isNotEmpty())) {
            f.extras
        } else {
            f.extras.ifEmpty { extras }
        },
        history = history + f.history,
        activity = act,
        syncedAt = f.syncedAt,
        isHidden = isHidden,
    )
}

/** Change since the last day we have a saved value for. Null when there is nothing to compare. */
fun TrackedAccount.deltaToday(): Long? {
    val today = Days.key()
    val now = history[today] ?: return null
    val prevKey = history.keys.filter { it < today }.maxOrNull() ?: return null
    val prev = history[prevKey] ?: return null
    return now - prev
}

fun TrackedAccount.eventsOn(offset: Int): Int = activity[Days.key(offset)] ?: 0

fun TrackedAccount.weekEvents(): Int = (0..6).sumOf { eventsOn(-it) }

/** Seven bars (oldest to newest), each 0..1, representing Followers/Subscribers count history over time. */
fun TrackedAccount.spark(days: Int = 7): List<Float> {
    val keys = history.keys.sorted()
    val values = (days - 1 downTo 0).map { off ->
        val day = Days.key(-off)
        keys.lastOrNull { it <= day }?.let { history[it] }
    }
    val known = values.filterNotNull()
    if (known.isEmpty()) {
        if (platform.fullScan) {
            val raw = (days - 1 downTo 0).map { eventsOn(-it).toFloat() }
            val max = raw.maxOrNull() ?: 0f
            return if (max <= 0f) raw.map { 0f } else raw.map { it / max }
        }
        return values.map { 0f }
    }
    val min = known.minOrNull() ?: 0L
    val max = known.maxOrNull() ?: 0L
    return values.map { v ->
        when {
            v == null -> 0f
            max == min -> 0.5f
            else -> 0.2f + 0.8f * (v - min).toFloat() / (max - min).toFloat()
        }
    }
}

fun combinedActivity(accounts: List<TrackedAccount>): Map<String, Int> {
    val out = HashMap<String, Int>()
    for (a in accounts) {
        val act = a.activity ?: continue
        for ((k, v) in act) {
            out[k] = (out[k] ?: 0) + v
        }
    }
    return out
}

fun computeStreak(days: Map<String, Int>): Streak {
    return try {
        val active = days.filterValues { it > 0 }.keys
        val nums = active.mapNotNull { Days.number(it) }.toSortedSet()
        if (nums.isEmpty()) return Streak(0, 0, 0, null)

        val today = Days.number(Days.key()) ?: nums.last()
        var cur = 0
        var d = if (nums.contains(today)) today else today - 1
        while (nums.contains(d)) {
            cur++
            d--
        }
        var longest = 0
        var run = 0
        var prev = Long.MIN_VALUE
        for (n in nums) {
            run = if (n == prev + 1) run + 1 else 1
            if (run > longest) longest = run
            prev = n
        }
        Streak(cur, longest, nums.size, active.minOrNull())
    } catch (e: Exception) {
        Streak(0, 0, 0, null)
    }
}

// ---------- Building snapshots from what the existing APIs already return ----------

object Sync {
    fun fromGit(user: GhUser, repos: List<GhRepo>, activity: Map<String, Int>) = Snapshot(
        name = user.name ?: user.login,
        avatarUrl = user.avatarUrl.takeIf { it.isNotBlank() },
        subtitle = user.bio ?: user.location,
        count = user.followers.toLong(),
        countLabel = "Followers",
        extras = buildList {
            add("Repos" to "%,d".format(repos.size))
            add("Stars" to "%,d".format(repos.sumOf { it.stars }))
            if (user.following > 0) add("Following" to "%,d".format(user.following))
        },
        activity = activity,
    )

    fun fromPreview(p: PublicProfile): Snapshot {
        val main = p.stats.firstOrNull { it.first == "Followers" || it.first == "Subscribers" }
        val count = parseCount(main?.second)?.takeIf { it > 0L }
        val extras = p.stats.mapNotNull { (label, value) ->
            if (label == main?.first) return@mapNotNull null
            val kept = label == "Following" || label == "Videos" || label == "Views"
            if (!kept) return@mapNotNull null
            if (label == "Following" && (parseCount(value) ?: 0L) <= 0L) return@mapNotNull null
            if (value.equals("Hidden", true) || parseCount(value) != null) label to value else null
        }
        return Snapshot(
            name = p.name,
            avatarUrl = p.imageUrl,
            subtitle = p.subtitle,
            count = count,
            countLabel = if (count != null) main?.first.orEmpty() else "",
            extras = extras,
            activity = null,
        )
    }

    fun fromLink(parsed: ParsedProfile) = Snapshot(
        name = "@" + parsed.handle.trimStart('@'),
        avatarUrl = null,
        subtitle = null,
        count = null,
        countLabel = "",
        extras = emptyList(),
        activity = null,
    )

    /** Re-reads one saved account. Returns null when this platform has nothing to refresh. */
    suspend fun fetch(acc: TrackedAccount, youtubeKey: String): Snapshot? {
        val parsed = ParsedProfile(acc.handle, acc.url)
        return when (acc.platform) {
            Platform.GITHUB -> {
                val u = GitHubApi.fetchUser(acc.handle)
                val r = GitHubApi.fetchRepos(acc.handle)
                val a = GitHubApi.fetchActivity(acc.handle)
                fromGit(u, r, a)
            }
            Platform.GITLAB -> {
                val (u, id) = GitLabApi.fetchUser(acc.handle)
                val r = GitLabApi.fetchRepos(id)
                val a = GitLabApi.fetchActivity(id, acc.handle)
                fromGit(u, r, a)
            }
            Platform.YOUTUBE ->
                if (youtubeKey.isBlank()) null else fromPreview(SocialApi.fetchYouTube(parsed, youtubeKey))
            Platform.INSTAGRAM, Platform.LINKEDIN, Platform.FACEBOOK, Platform.PORTFOLIO, Platform.X ->
                fromPreview(SocialApi.fetchPreview(acc.platform, parsed))
        }
    }
}

// ---------- Persistence (SharedPreferences, JSON) ----------

object Store {
    private const val USER = "user_email"

    fun loadUser(p: SharedPreferences): UserSession? {
        val email = p.getString(USER, null)?.takeIf { it.isNotBlank() } ?: return null
        val name = p.getString("name_$email", null)?.takeIf { it.isNotBlank() } ?: nameFromEmail(email)
        val avatar = p.getString("avatar_$email", null)?.takeIf { it.isNotBlank() }
        return UserSession(email, name, avatar)
    }

    fun saveUser(p: SharedPreferences, u: UserSession?) {
        val e = p.edit()
        if (u == null) {
            e.remove(USER)
        } else {
            e.putString(USER, u.email)
                .putString("name_${u.email}", u.name)
                .putString("avatar_${u.email}", u.avatarUrl ?: "")
        }
        e.apply()
    }

    fun loadAccounts(p: SharedPreferences, email: String): List<TrackedAccount> {
        val raw = p.getString("accounts_$email", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> fromJson(arr.getJSONObject(i)) }.distinctBy { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAccounts(p: SharedPreferences, email: String, list: List<TrackedAccount>) {
        val arr = JSONArray()
        list.distinctBy { it.id }.forEach { arr.put(toJson(it)) }
        p.edit().putString("accounts_$email", arr.toString()).apply()
    }

    /** Returns true if this account (by ID, URL, or platform+handle) is already saved under a different email. */
    fun isTrackedByAnotherAccount(p: SharedPreferences, currentEmail: String, acc: TrackedAccount): Boolean {
        val allKeys = p.all.keys.filter { it.startsWith("accounts_") }
        val targetEmailKey = "accounts_${currentEmail.trim().lowercase()}"
        val normUrl = acc.url.trim().lowercase().trimEnd('/')
        val normHandle = acc.handle.trim().lowercase().trimStart('@')

        for (key in allKeys) {
            if (key == targetEmailKey) continue
            val raw = p.getString(key, null) ?: continue
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val other = fromJson(arr.getJSONObject(i)) ?: continue
                    val otherNormUrl = other.url.trim().lowercase().trimEnd('/')
                    val otherNormHandle = other.handle.trim().lowercase().trimStart('@')
                    if (other.id == acc.id ||
                        (other.platform == acc.platform && otherNormHandle == normHandle) ||
                        (normUrl.isNotBlank() && otherNormUrl == normUrl)
                    ) {
                        return true
                    }
                }
            } catch (_: Exception) {}
        }
        return false
    }

    private fun toJson(a: TrackedAccount) = JSONObject().apply {
        put("id", a.id)
        put("platform", a.platform.name)
        put("handle", a.handle)
        put("url", a.url)
        put("name", a.name)
        put("avatar", a.avatarUrl ?: JSONObject.NULL)
        put("subtitle", a.subtitle ?: JSONObject.NULL)
        put("count", a.count ?: JSONObject.NULL)
        put("countLabel", a.countLabel)
        put("addedAt", a.addedAt)
        put("syncedAt", a.syncedAt)
        put("extras", JSONArray().also { arr ->
            a.extras.forEach { (k, v) -> arr.put(JSONArray().put(k).put(v)) }
        })
        put("history", JSONObject().also { o -> a.history.forEach { (k, v) -> o.put(k, v) } })
        put("activity", JSONObject().also { o -> a.activity.forEach { (k, v) -> o.put(k, v) } })
        put("isHidden", a.isHidden)
    }

    private fun fromJson(o: JSONObject): TrackedAccount? {
        return try {
            val pName = o.optString("platform")
            val platform = Platform.entries.firstOrNull { it.name == pName } ?: return null
            val extras = o.optJSONArray("extras")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val pair = arr.getJSONArray(i)
                    pair.getString(0) to pair.getString(1)
                }
            } ?: emptyList()
            val history = o.optJSONObject("history")?.let { h ->
                h.keys().asSequence().associateWith { k -> h.getLong(k) }
            } ?: emptyMap()
            val activity = o.optJSONObject("activity")?.let { h ->
                h.keys().asSequence().associateWith { k -> h.getInt(k) }
            } ?: emptyMap()
            TrackedAccount(
                id = o.getString("id"),
                platform = platform,
                handle = o.getString("handle"),
                url = o.getString("url"),
                name = o.optString("name"),
                avatarUrl = o.str("avatar"),
                subtitle = o.str("subtitle"),
                count = if (o.isNull("count")) null else o.getLong("count"),
                countLabel = o.optString("countLabel", "Followers"),
                extras = extras,
                history = history,
                activity = activity,
                addedAt = o.optString("addedAt"),
                syncedAt = o.optLong("syncedAt"),
                isHidden = o.optBoolean("isHidden", false),
            )
        } catch (e: Exception) {
            null
        }
    }
}

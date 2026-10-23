package com.trackme.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EarnedBadge(val id: String, val earnedAt: Long)

/**
 * Achievements earned on this phone.
 * A scan is a saved tag. An upload is a stored file. Activity is the number on the Activity card.
 */
object BadgeBook {
    const val SCAN_1 = "scan_1"
    const val SCAN_5 = "scan_5"
    const val SIZE_100 = "size_100"
    const val DAYS_7 = "days_7"
    private const val ACT = "act_"

    fun activityId(thousands: Int) = "$ACT$thousands"

    fun rank(id: String): Int = when (id) {
        SCAN_1 -> 0
        SCAN_5 -> 1
        SIZE_100 -> 2
        DAYS_7 -> 3
        else -> 10 + (id.removePrefix(ACT).toIntOrNull() ?: 99)
    }

    fun title(id: String): String = when (id) {
        SCAN_1 -> "First achievement"
        SCAN_5 -> "Second achievement"
        SIZE_100 -> "100 MB"
        DAYS_7 -> "7 days"
        else -> if (id.startsWith(ACT)) "Activity milestone" else "Badge"
    }

    fun detail(id: String): String = when (id) {
        SCAN_1 -> "1 scan or 1 upload"
        SCAN_5 -> "5 scans or 5 uploads"
        SIZE_100 -> "Uploads reached 100 MB"
        DAYS_7 -> "Active in the app for 7 days"
        else -> if (id.startsWith(ACT)) "Activity reached ${id.removePrefix(ACT)}k" else ""
    }

    fun dateLabel(at: Long): String {
        if (at <= 0L) return "Not yet"
        return SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(at))
    }

    /** Keeps the day a badge was first earned. Newly crossed limits are added with [now]. */
    fun evaluate(
        scans: Int,
        uploads: Int,
        activity: Long,
        bytes: Long,
        activeDays: Int,
        existing: List<EarnedBadge>,
        now: Long = System.currentTimeMillis(),
    ): List<EarnedBadge> {
        val kept = existing.associateBy { it.id }
        val ids = ArrayList<String>()
        if (scans >= 1 || uploads >= 1) ids += SCAN_1
        if (scans >= 5 || uploads >= 5) ids += SCAN_5
        val steps = (activity / 10_000L).toInt().coerceIn(0, 200)
        for (n in 1..steps) ids += activityId(n * 10)
        if (bytes >= 100L * 1024L * 1024L) ids += SIZE_100
        if (activeDays >= 7) ids += DAYS_7
        return ids.map { id -> kept[id] ?: EarnedBadge(id, now) }
    }

    /** Earned badges, then the fixed ones still ahead, then the next 10k step. */
    fun showcase(earned: List<EarnedBadge>, activity: Long): List<Pair<EarnedBadge?, String>> {
        val have = earned.associateBy { it.id }
        val rows = ArrayList<Pair<EarnedBadge?, String>>()
        earned.sortedBy { rank(it.id) }.forEach { rows += it to it.id }
        listOf(SCAN_1, SCAN_5, SIZE_100, DAYS_7).forEach { id ->
            if (id !in have) rows += null to id
        }
        val next = ((activity / 10_000L).toInt() + 1).coerceAtMost(200) * 10
        val nextId = activityId(next)
        if (nextId !in have && next <= 2000) rows += null to nextId
        return rows
    }

    fun toJson(list: List<EarnedBadge>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("at", it.earnedAt)) }
        return arr.toString()
    }

    fun fromJson(raw: String?): List<EarnedBadge> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val known = id == SCAN_1 || id == SCAN_5 || id == SIZE_100 || id == DAYS_7 || id.startsWith(ACT)
                    if (!known) continue
                    add(EarnedBadge(id, o.optLong("at")))
                }
            }
        }.getOrDefault(emptyList())
    }
}

package com.trackme.app.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Calendar
import java.util.Locale

/** Today's real on-screen time from Android usage access. Null when it cannot be read. */
object ScreenTime {
    fun hasAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Milliseconds the phone was in use since local midnight. Null when usage access has no data. */
    fun todayMillis(context: Context): Long? {
        if (!hasAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = startOfToday()
        val now = System.currentTimeMillis()
        val stats = usm.queryAndAggregateUsageStats(start, now)
        if (stats.isEmpty()) return null
        var total = 0L
        for (usage in stats.values) {
            val visible = if (Build.VERSION.SDK_INT >= 29) usage.totalTimeVisible else usage.totalTimeInForeground
            if (visible > 0L) total += visible
        }
        return total.takeIf { it > 0L }
    }

    /** 90 minutes -> "1.5hr", 60 minutes -> "1hr", 18 minutes -> "18m". */
    fun format(millis: Long): String {
        if (millis < 60_000L) return ""
        val hours = millis / 3_600_000.0
        if (hours < 1.0) {
            val minutes = (millis / 60_000L).toInt()
            return "${minutes}m"
        }
        val tenths = (hours * 10).toLong()
        return if (tenths % 10L == 0L) "${tenths / 10}hr" else String.format(Locale.US, "%.1fhr", tenths / 10.0)
    }

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

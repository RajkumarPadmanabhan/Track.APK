package com.trackme.app.data

import java.util.Locale

fun formatMetricCount(count: Long): String {
    val a = Math.abs(count)
    if (a < 1000) return count.toString()
    if (a < 1_000_000) {
        val v = count / 1000.0
        val s = String.format(Locale.US, "%.2f", v).trimEnd('0')
        val formatted = if (s.endsWith(".")) s + "0" else s
        return formatted + "k"
    }
    val v = count / 1_000_000.0
    val s = String.format(Locale.US, "%.2f", v).trimEnd('0')
    val formatted = if (s.endsWith(".")) s + "0" else s
    return formatted + "M"
}

fun formatMetricCount(count: Int): String = formatMetricCount(count.toLong())

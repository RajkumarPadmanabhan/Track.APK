package com.trackme.app.data

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

fun formatMetricCount(count: Long): String {
    if (count < 1000) return count.toString()
    val exp = (ln(count.toDouble()) / ln(1000.0)).toInt().coerceIn(1, 6)
    val formatChar = "kMGTPE"[exp - 1]
    val value = count / 1000.0.pow(exp.toDouble())
    return String.format(Locale.US, "%.1f%c", value, formatChar)
}

fun formatMetricCount(count: Int): String = formatMetricCount(count.toLong())

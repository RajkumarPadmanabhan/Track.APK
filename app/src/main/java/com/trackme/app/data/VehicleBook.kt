package com.trackme.app.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

/** A plate saved on this phone. A date is stored only when the user typed it or a file contained it. */
data class VehicleRecord(
    val id: String,
    val plate: String,
    val kind: String,
    val model: String = "",
    val fuel: String = "",
    val insuranceOn: String = "",
    val pollutionOn: String = "",
    val rcOn: String = "",
    val facts: String = "",
    val insuranceFile: String = "",
    val pollutionFile: String = "",
    val rcFile: String = "",
)

enum class PaperUrgency { NONE, OK, DUE, OVERDUE }

data class VehicleLine(val text: String, val urgency: PaperUrgency)

data class PaperDay(val year: Int, val month: Int, val day: Int) : Comparable<PaperDay> {
    override fun compareTo(other: PaperDay): Int {
        if (year != other.year) return year - other.year
        if (month != other.month) return month - other.month
        return day - other.day
    }

    fun label(): String = "$day ${MONTHS[month - 1]} $year"

    companion object {
        val MONTHS = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    }
}

object VehicleBook {
    private val standard = Regex("[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{3,4}")
    private val bharat = Regex("[0-9]{2}BH[0-9]{4}[A-Z]{1,2}")
    private val named = Regex(
        """\b(\d{1,2})(?:st|nd|rd|th)?[./\-\s]+(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*[./\-\s,]+(\d{2,4})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val monthFirst = Regex(
        """\b(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\s+(\d{1,2})(?:st|nd|rd|th)?[,\s]+(\d{4})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val iso = Regex("""\b(\d{4})[./-](\d{1,2})[./-](\d{1,2})\b""")
    private val numeric = Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})\b""")
    private val dueWindow = 30

    fun normalize(raw: String): String = raw.uppercase().filter { it.isLetterOrDigit() }

    fun isPlate(plate: String): Boolean = standard.matches(plate) || bharat.matches(plate)

    /** Pulls a plate out of document text. Returns null when none is readable. */
    fun findPlate(raw: String): String? {
        val compact = normalize(raw)
        return listOfNotNull(standard.find(compact)?.value, bharat.find(compact)?.value)
            .maxByOrNull { it.length }
    }

    fun warn(row: VehicleRecord): Boolean {
        return listOf(row.insuranceOn, row.pollutionOn, row.rcOn).any { raw ->
            val urgency = urgencyOf(raw)
            urgency == PaperUrgency.DUE || urgency == PaperUrgency.OVERDUE
        }
    }

    fun lines(row: VehicleRecord): List<VehicleLine> {
        val out = mutableListOf<VehicleLine>()
        out += VehicleLine(if (row.kind == "bike") "Bike" else "Car", PaperUrgency.NONE)
        if (row.model.isNotBlank()) out += VehicleLine(row.model, PaperUrgency.NONE)
        if (row.fuel.isNotBlank()) out += VehicleLine(row.fuel, PaperUrgency.NONE)
        watch("Insurance", row.insuranceOn)?.let { out += it }
        watch("Pollution test", row.pollutionOn)?.let { out += it }
        watch("RC", row.rcOn)?.let { out += it }
        row.facts.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            out += VehicleLine(line, PaperUrgency.NONE)
        }
        return out
    }

    /** How a saved or typed date stands against today. Blank and unreadable dates are not treated as due. */
    fun watch(label: String, raw: String): VehicleLine? {
        if (raw.isBlank()) return null
        val day = parseDay(raw) ?: return VehicleLine("$label $raw", PaperUrgency.NONE)
        val left = daysUntil(day)
        val whenText = day.label()
        return when {
            left < 0 -> VehicleLine("$label overdue · $whenText", PaperUrgency.OVERDUE)
            left == 0 -> VehicleLine("$label due today · $whenText", PaperUrgency.DUE)
            left == 1 -> VehicleLine("$label due in 1 day · $whenText", PaperUrgency.DUE)
            left <= dueWindow -> VehicleLine("$label due in $left days · $whenText", PaperUrgency.DUE)
            else -> VehicleLine("$label up to date · $whenText", PaperUrgency.OK)
        }
    }

    fun parseDay(raw: String): PaperDay? = findDays(raw).firstOrNull()

    fun findDays(raw: String): List<PaperDay> {
        val found = mutableListOf<PaperDay>()
        fun add(day: PaperDay?) {
            if (day != null && found.none { it == day }) found += day
        }
        named.findAll(raw).forEach { match ->
            add(dayOf(match.groupValues[1].toInt(), monthNumber(match.groupValues[2]), yearNumber(match.groupValues[3].toInt())))
        }
        monthFirst.findAll(raw).forEach { match ->
            add(dayOf(match.groupValues[2].toInt(), monthNumber(match.groupValues[1]), match.groupValues[3].toInt()))
        }
        iso.findAll(raw).forEach { match ->
            add(dayOf(match.groupValues[3].toInt(), match.groupValues[2].toInt(), match.groupValues[1].toInt()))
        }
        numeric.findAll(raw).forEach { match ->
            val first = match.groupValues[1].toInt()
            val second = match.groupValues[2].toInt()
            val year = yearNumber(match.groupValues[3].toInt())
            val (day, month) = if (first > 12) first to second else if (second > 12) second to first else first to second
            add(dayOf(day, month, year))
        }
        return found
    }

    fun mergeFacts(old: String, fresh: String): String {
        if (fresh.isBlank()) return old
        val prefixes = fresh.lineSequence().mapNotNull { line ->
            line.substringBefore(" · ").takeIf { " · " in line }
        }.toSet()
        val kept = old.lineSequence().filter { line ->
            line.isNotBlank() && line.substringBefore(" · ") !in prefixes
        }
        return (fresh.lineSequence().filter { it.isNotBlank() } + kept).take(30).joinToString("\n")
    }

    fun load(prefs: SharedPreferences, email: String): List<VehicleRecord> {
        val raw = prefs.getString("vehicles_$email", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                VehicleRecord(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    plate = o.optString("plate"),
                    kind = if (o.optString("kind") == "bike") "bike" else "car",
                    model = o.optString("model"),
                    fuel = o.optString("fuel"),
                    insuranceOn = o.optString("insuranceOn"),
                    pollutionOn = o.optString("pollutionOn"),
                    rcOn = o.optString("rcOn"),
                    facts = o.optString("facts"),
                    insuranceFile = o.optString("insuranceFile"),
                    pollutionFile = o.optString("pollutionFile"),
                    rcFile = o.optString("rcFile"),
                )
            }.filter { it.plate.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun save(prefs: SharedPreferences, email: String, rows: List<VehicleRecord>) {
        val arr = JSONArray()
        rows.forEach { row ->
            arr.put(JSONObject().apply {
                put("id", row.id)
                put("plate", row.plate)
                put("kind", row.kind)
                put("model", row.model)
                put("fuel", row.fuel)
                put("insuranceOn", row.insuranceOn)
                put("pollutionOn", row.pollutionOn)
                put("rcOn", row.rcOn)
                put("facts", row.facts)
                put("insuranceFile", row.insuranceFile)
                put("pollutionFile", row.pollutionFile)
                put("rcFile", row.rcFile)
            })
        }
        prefs.edit().putString("vehicles_$email", arr.toString()).apply()
    }

    private fun urgencyOf(raw: String): PaperUrgency = watch("", raw)?.urgency ?: PaperUrgency.NONE

    private fun daysUntil(day: PaperDay): Int {
        val now = Calendar.getInstance()
        return julian(day.year, day.month, day.day) -
            julian(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
    }

    private fun julian(year: Int, month: Int, day: Int): Int {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    }

    private fun yearNumber(year: Int): Int = when {
        year >= 100 -> year
        year >= 70 -> 1900 + year
        else -> 2000 + year
    }

    private fun monthNumber(raw: String): Int {
        val key = raw.lowercase().take(3)
        return PaperDay.MONTHS.indexOfFirst { it.lowercase() == key } + 1
    }

    private fun dayOf(day: Int, month: Int, year: Int): PaperDay? {
        if (month !in 1..12 || day !in 1..31 || year !in 1990..2100) return null
        val calendar = Calendar.getInstance()
        calendar.isLenient = false
        return try {
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month - 1)
            calendar.set(Calendar.DAY_OF_MONTH, day)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.timeInMillis
            PaperDay(year, month, day)
        } catch (_: Exception) {
            null
        }
    }
}

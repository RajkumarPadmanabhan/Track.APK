package com.trackme.app.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class ExpenseEntry(
    val id: String,
    val title: String,
    val category: String,
    val price: Double,
    val at: Long,
)

object ExpenseBook {
    val categories = listOf("Product", "Dress", "Purchase", "Shopping")

    data class State(
        val enabled: Boolean = false,
        val limit: Double = 0.0,
        val items: List<ExpenseEntry> = emptyList(),
    )

    fun load(prefs: SharedPreferences, email: String): State {
        val raw = prefs.getString(key(email), null) ?: return State()
        return try {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("items") ?: JSONArray()
            val items = (0 until arr.length()).map { i ->
                val row = arr.getJSONObject(i)
                ExpenseEntry(
                    id = row.getString("id"),
                    title = row.optString("title"),
                    category = row.optString("category", categories.first()),
                    price = row.optDouble("price", 0.0),
                    at = row.optLong("at", 0L),
                )
            }
            State(
                enabled = o.optBoolean("on", false),
                limit = o.optDouble("limit", 0.0),
                items = items.sortedByDescending { it.at },
            )
        } catch (_: Exception) {
            State()
        }
    }

    fun save(prefs: SharedPreferences, email: String, state: State) {
        val arr = JSONArray()
        state.items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("category", item.category)
                put("price", item.price)
                put("at", item.at)
            })
        }
        val o = JSONObject()
        o.put("on", state.enabled)
        o.put("limit", state.limit)
        o.put("items", arr)
        prefs.edit().putString(key(email), o.toString()).apply()
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun money(value: Double): String {
        val whole = value % 1.0 == 0.0
        val body = if (whole) value.toLong().toString() else String.format(Locale.US, "%.2f", value)
        return "₹$body"
    }

    fun spent(items: List<ExpenseEntry>, year: Int, month: Int): Double =
        items.filter { sameMonth(it.at, year, month) }.sumOf { it.price }

    fun spentWeek(items: List<ExpenseEntry>, now: Long = System.currentTimeMillis()): Double {
        val (start, end) = weekBounds(now)
        return items.filter { it.at in start until end }.sumOf { it.price }
    }

    fun spentYear(items: List<ExpenseEntry>, year: Int): Double =
        items.filter { yearOf(it.at) == year }.sumOf { it.price }

    fun csv(items: List<ExpenseEntry>, heading: String): String {
        val lines = StringBuilder()
        lines.append(heading).append('\n')
        lines.append("Date,Category,Details,Price\n")
        items.sortedBy { it.at }.forEach { item ->
            lines.append(dateText(item.at)).append(',')
            lines.append(csv(item.category)).append(',')
            lines.append(csv(item.title)).append(',')
            lines.append(String.format(Locale.US, "%.2f", item.price)).append('\n')
        }
        val total = items.sumOf { it.price }
        lines.append(",,Total,").append(String.format(Locale.US, "%.2f", total)).append('\n')
        return lines.toString()
    }

    fun sameMonth(at: Long, year: Int, month: Int): Boolean {
        val c = Calendar.getInstance()
        c.timeInMillis = at
        return c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
    }

    fun yearOf(at: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = at
        return c.get(Calendar.YEAR)
    }

    fun monthLabel(year: Int, month: Int): String {
        val c = Calendar.getInstance()
        c.set(Calendar.YEAR, year)
        c.set(Calendar.MONTH, month)
        c.set(Calendar.DAY_OF_MONTH, 1)
        return java.text.SimpleDateFormat("MMMM yyyy", Locale.US).format(c.time)
    }

    fun weekBounds(now: Long): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) c.add(Calendar.DAY_OF_MONTH, -1)
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_MONTH, 7)
        return start to c.timeInMillis
    }

    private fun dateText(at: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(at)

    private fun csv(value: String): String {
        val clean = value.replace("\"", "\"\"")
        return "\"$clean\""
    }

    private fun key(email: String) = "expenses_$email"
}

package com.trackme.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads cart products and orders from an Amazon or Flipkart page.
 * Amounts that are not on the page are left out. A missing total is never stored as 0.
 */
object ShopReader {
    suspend fun fetch(rawUrl: String): List<ShopEntry> = withContext(Dispatchers.IO) {
        try {
            val html = load(rawUrl) ?: return@withContext emptyList()
            read(html)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private val money = Regex(
        """(?:₹|Rs\.?|INR)\s*([0-9]{1,3}(?:,[0-9]{2,3})+(?:\.\d{1,2})?|[0-9]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val cartTitle = Regex(
        """(?:sc-product-title|_4rR01T|B_NuCI)[^>]*>([^<]{2,160})""",
        RegexOption.IGNORE_CASE,
    )
    private val productName = Regex(
        "\"(?:productName|itemName)\"\\s*:\\s*\"([^\"\\\\]{2,160})\"",
        RegexOption.IGNORE_CASE,
    )
    private val amazonOrder = Regex("""\b(\d{3}-\d{7}-\d{7})\b""")
    private val flipkartOrder = Regex("""\b(OD\d{15,22})\b""")

    private fun read(html: String): List<ShopEntry> {
        val cart = LinkedHashMap<String, ShopEntry>()
        fun addCart(title: String, at: Int) {
            val clean = title.trim().replace(Regex("\\s+"), " ")
            if (clean.length < 2 || cart.containsKey(clean.lowercase())) return
            val price = firstMoney(html, at, 700)
            cart[clean.lowercase()] = ShopEntry("Cart", clean, price.orEmpty())
            if (cart.size >= 20) return
        }
        for (match in cartTitle.findAll(html)) {
            if (cart.size >= 20) break
            addCart(match.groupValues[1], match.range.last)
        }
        for (match in productName.findAll(html)) {
            if (cart.size >= 20) break
            addCart(match.groupValues[1], match.range.last)
        }

        val orders = LinkedHashMap<String, ShopEntry>()
        fun addOrder(id: String, at: Int) {
            if (orders.containsKey(id) || orders.size >= 20) return
            val price = largestMoney(html, at, 900)
            orders[id] = ShopEntry("Order", "Order $id", price.orEmpty())
        }
        for (match in amazonOrder.findAll(html)) addOrder(match.groupValues[1], match.range.last)
        for (match in flipkartOrder.findAll(html)) addOrder(match.groupValues[1], match.range.last)

        return cart.values.toList() + orders.values.toList()
    }

    private fun firstMoney(html: String, start: Int, window: Int): String? {
        val slice = html.substring(start, minOf(html.length, start + window))
        val raw = money.find(slice)?.groupValues?.get(1) ?: return null
        val amount = parseMoney(raw)
        return if (amount > 0L) formatRupees(amount) else null
    }

    private fun largestMoney(html: String, start: Int, window: Int): String? {
        val slice = html.substring(start, minOf(html.length, start + window))
        val best = money.findAll(slice).map { parseMoney(it.groupValues[1]) }.filter { it > 0L }.maxOrNull()
        return best?.let { formatRupees(it) }
    }

    private fun load(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; TrackMe) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            setRequestProperty("Accept", "text/html")
            setRequestProperty("Accept-Language", "en-IN,en;q=0.9")
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (sb.length < 800_000) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}

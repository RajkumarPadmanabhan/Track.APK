package com.trackme.app.data

import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** One share: who sent which file or note, and who received it. */
data class ShareRecord(
    val code: String,
    val kind: String,
    val name: String,
    val fromEmail: String,
    val toEmail: String,
    val at: Long,
)

/**
 * A short code stored in the same Firebase database the app already uses.
 * The file itself is removed after the other person receives it. The history row stays.
 */
object ShareRelay {
    private const val BASE = "https://trackme-1c59a-default-rtdb.firebaseio.com"
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    const val MAX_BYTES = 4 * 1024 * 1024

    fun message(kind: String, name: String, code: String): String {
        val title = when (kind) {
            "profile" -> "TrackMe profile: $name"
            "file" -> "TrackMe file: $name"
            else -> "TrackMe note: $name"
        }
        return "$title\nCode: $code\nHold the scan button in TrackMe and enter the code."
    }

    fun normalizeCode(raw: String): String {
        val upper = raw.uppercase(Locale.US)
        val labeled = Regex("CODE[:\\s]+([$ALPHABET]{6})").find(upper)
        if (labeled != null) return labeled.groupValues[1]
        return upper.filter { it in ALPHABET }.take(6)
    }

    fun create(
        fromEmail: String,
        fromName: String,
        kind: String,
        name: String,
        mime: String,
        bytes: ByteArray?,
        text: String?,
    ): String {
        val now = System.currentTimeMillis()
        repeat(5) {
            val code = randomCode()
            if (getObject("shares/$code") != null) return@repeat
            val body = JSONObject().apply {
                put("fromEmail", fromEmail)
                put("fromName", fromName)
                put("toEmail", "")
                put("toName", "")
                put("kind", kind)
                put("name", name.take(80))
                put("mime", mime)
                put("createdAt", now)
                put("expiresAt", now + DAY_MS)
                put("claimedAt", 0)
                if (bytes != null) put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                if (!text.isNullOrBlank()) put("text", text.take(500))
            }
            put("shares/$code", body)
            val index = indexBody(code, kind, name, fromEmail, "", now)
            put("shareIndex/${key(fromEmail)}/$code", index)
            return code
        }
        throw IOException("Couldn't make a share code.")
    }

    fun history(email: String): List<ShareRecord> {
        val obj = getObject("shareIndex/${key(email)}") ?: return emptyList()
        val rows = mutableListOf<ShareRecord>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val code = keys.next()
            val row = obj.optJSONObject(code) ?: continue
            rows += ShareRecord(
                code = code,
                kind = row.optString("kind"),
                name = row.optString("name"),
                fromEmail = row.optString("fromEmail"),
                toEmail = row.optString("toEmail"),
                at = row.optLong("at"),
            )
        }
        return rows.sortedByDescending { it.at }
    }

    /** Downloads a code and writes the receiver onto the history row. File bytes are then deleted. */
    fun claim(code: String, toEmail: String, toName: String): ClaimedShare {
        val clean = normalizeCode(code)
        if (clean.length != 6) throw IOException("Enter the 6-character code.")
        val obj = getObject("shares/$clean") ?: throw IOException("That code was not found.")
        val fromEmail = obj.optString("fromEmail")
        if (fromEmail.equals(toEmail, ignoreCase = true)) {
            throw IOException("That code is one you sent.")
        }
        val already = obj.optString("toEmail")
        if (already.isNotBlank() && !already.equals(toEmail, ignoreCase = true)) {
            throw IOException("Someone already used this code.")
        }
        val kind = obj.optString("kind")
        val name = obj.optString("name").ifBlank { "Shared" }
        val now = System.currentTimeMillis()
        if (already.isBlank() && obj.optLong("expiresAt") in 1 until now) {
            throw IOException("That code expired.")
        }
        val bytes = obj.optString("data").takeIf { it.isNotBlank() }?.let {
            Base64.decode(it, Base64.NO_WRAP)
        }
        val text = obj.optString("text")
        if (already.isBlank()) {
            patch("shares/$clean", JSONObject().apply {
                put("toEmail", toEmail)
                put("toName", toName)
                put("claimedAt", now)
                put("data", JSONObject.NULL)
            })
            val senderKey = key(fromEmail)
            patch("shareIndex/$senderKey/$clean", JSONObject().apply {
                put("toEmail", toEmail)
            })
            put(
                "shareIndex/${key(toEmail)}/$clean",
                indexBody(clean, kind, name, fromEmail, toEmail, obj.optLong("createdAt", now)),
            )
        }
        return ClaimedShare(
            code = clean,
            kind = kind,
            name = name,
            fromEmail = fromEmail,
            fromName = obj.optString("fromName"),
            mime = obj.optString("mime"),
            text = text,
            bytes = bytes,
            already = already.equals(toEmail, ignoreCase = true),
            createdAt = obj.optLong("createdAt", now),
        )
    }

    private fun indexBody(code: String, kind: String, name: String, fromEmail: String, toEmail: String, at: Long): JSONObject {
        return JSONObject().apply {
            put("code", code)
            put("kind", kind)
            put("name", name)
            put("fromEmail", fromEmail)
            put("toEmail", toEmail)
            put("at", at)
        }
    }

    private fun randomCode(): String = buildString {
        repeat(6) { append(ALPHABET[kotlin.random.Random.nextInt(ALPHABET.length)]) }
    }

    private fun key(email: String): String {
        val clean = email.lowercase(Locale.US).map { ch ->
            if (ch.isLetterOrDigit()) ch else '_'
        }.joinToString("")
        return clean.ifBlank { "user" }
    }

    private fun getObject(path: String): JSONObject? {
        val text = call("GET", path, null)
        if (text.isBlank() || text == "null") return null
        return JSONObject(text)
    }

    private fun put(path: String, body: JSONObject) {
        call("PUT", path, body.toString())
    }

    private fun patch(path: String, body: JSONObject) {
        call("PATCH", path, body.toString())
    }

    private fun call(method: String, path: String, body: String?): String {
        val conn = (URL("$BASE/$path.json").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 60000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (body != null) doOutput = true
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("Share failed ($code).")
            return text
        } finally {
            conn.disconnect()
        }
    }
}

data class ClaimedShare(
    val code: String,
    val kind: String,
    val name: String,
    val fromEmail: String,
    val fromName: String,
    val mime: String,
    val text: String,
    val bytes: ByteArray?,
    val already: Boolean,
    val createdAt: Long,
)

package com.trackme.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.UUID
import android.net.Uri
import android.provider.OpenableColumns

/** 842 B, 12.4 KB, 3.2 MB. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(bytes)
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Saved PDFs live in the app's private folder under a generated name.
 * They are stored compressed, and the original bytes are checked by hash
 * every time the file is opened or downloaded.
 */
object UploadedFileStore {
    private const val MAX_BYTES = 15L * 1024L * 1024L
    const val MAX_LABEL = "15 MB"

    fun maxBytes(): Long = MAX_BYTES

    fun dir(context: Context): File {
        val dir = File(context.filesDir, "uploads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun storedFile(context: Context, storedName: String): File = File(dir(context), storedName)

    fun load(prefs: SharedPreferences, email: String): List<UploadedFile> {
        val raw = prefs.getString("files_$email", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                UploadedFile(
                    id = o.getString("id"),
                    displayName = o.getString("displayName"),
                    storedName = o.getString("storedName"),
                    originalSize = o.getLong("originalSize"),
                    storedSize = o.getLong("storedSize"),
                    pageCount = o.optInt("pageCount", 0),
                    sha256 = o.getString("sha256"),
                    uploadedAt = o.optLong("uploadedAt", 0L),
                    portfolioName = o.str("portfolioName"),
                    mimeType = o.str("mimeType") ?: "application/octet-stream",
                    extension = o.str("extension") ?: "",
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(prefs: SharedPreferences, email: String, files: List<UploadedFile>) {
        val arr = JSONArray()
        files.forEach { f ->
            arr.put(JSONObject().apply {
                put("id", f.id)
                put("displayName", f.displayName)
                put("storedName", f.storedName)
                put("originalSize", f.originalSize)
                put("storedSize", f.storedSize)
                put("pageCount", f.pageCount)
                put("sha256", f.sha256)
                put("uploadedAt", f.uploadedAt)
                put("portfolioName", f.portfolioName ?: JSONObject.NULL)
                put("mimeType", f.mimeType)
                put("extension", f.extension)
            })
        }
        prefs.edit().putString("files_$email", arr.toString()).apply()
    }

    /**
     * Copies the picked file into private storage as gzip.
     * The bytes and picture quality stay the same. The stored name is generated,
     * and the file is never executed.
     */
    fun import(context: Context, uri: Uri): UploadedFile {
        val rawName = displayName(context, uri)
        val display = sanitizeName(rawName)
        val ext = display.substringAfterLast('.', "")
            .lowercase(Locale.US)
            .filter { it.isLetterOrDigit() }
            .take(8)
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val id = UUID.randomUUID().toString()
        val temp = File(context.cacheDir, "upload-$id.bin")
        try {
            var size = 0L
            val input = context.contentResolver.openInputStream(uri)
                ?: throw FileStoreException("Couldn't read that file.")
            input.use { stream ->
                FileOutputStream(temp).use { out ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = stream.read(buf)
                        if (n < 0) break
                        size += n
                        if (size > MAX_BYTES) throw FileStoreException("That file is over $MAX_LABEL.")
                        out.write(buf, 0, n)
                    }
                }
            }
            if (size <= 0L) throw FileStoreException("That file is empty.")
            val hash = sha256Hex(temp)
            val storedName = "$id.bin.gz"
            val stored = storedFile(context, storedName)
            GZIPOutputStream(FileOutputStream(stored)).use { gzip ->
                FileInputStream(temp).use { it.copyTo(gzip) }
            }
            stored.setExecutable(false, false)
            return UploadedFile(
                id = id,
                displayName = display,
                storedName = storedName,
                originalSize = size,
                storedSize = stored.length(),
                pageCount = 0,
                sha256 = hash,
                uploadedAt = System.currentTimeMillis(),
                portfolioName = null,
                mimeType = mime,
                extension = ext,
            )
        } finally {
            temp.delete()
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val name = it.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        }
        return "file"
    }

    private fun sanitizeName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        val clean = base.filter { it.code in 32..126 && it !in "\\/:*?\"<>|" }.take(80)
        return clean.ifBlank { "file" }
    }

    fun deleteStored(context: Context, file: UploadedFile) {
        runCatching { storedFile(context, file.storedName).delete() }
    }

    /**
     * Writes the exact original PDF bytes to [outFile] and proves they are
     * unchanged by checking the saved SHA-256 before returning.
     */
    fun writeOriginal(context: Context, file: UploadedFile, outFile: File): Boolean {
        val stored = storedFile(context, file.storedName)
        if (!stored.exists()) return false
        outFile.parentFile?.mkdirs()
        GZIPInputStream(FileInputStream(stored)).use { input ->
            FileOutputStream(outFile).use { input.copyTo(it) }
        }
        if (outFile.length() != file.originalSize) return false
        return sha256Hex(outFile).equals(file.sha256, ignoreCase = true)
    }
}

class FileStoreException(message: String) : Exception(message)

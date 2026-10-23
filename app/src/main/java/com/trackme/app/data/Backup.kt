package com.trackme.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A zip the user can keep. It holds the saved tags and the uploaded files.
 * Restoring it on a later sign-in puts that data back on the phone.
 */
object Backup {
    private val safeName = Regex("^[A-Za-z0-9._-]{1,80}$")

    fun write(context: Context, prefs: android.content.SharedPreferences, email: String, out: java.io.OutputStream) {
        val accounts = prefs.getString("accounts_$email", "[]") ?: "[]"
        val files = prefs.getString("files_$email", "[]") ?: "[]"
        val manifest = JSONObject()
        manifest.put("version", 1)
        manifest.put("exportedAt", System.currentTimeMillis())
        manifest.put("accounts", JSONArray(accounts))
        manifest.put("files", JSONArray(files))
        manifest.put("newsTopics", prefs.getString("news_topics", "") ?: "")
        manifest.put("theme", prefs.getString("theme_color", "") ?: "")
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val arr = JSONArray(files)
            for (i in 0 until arr.length()) {
                val name = arr.getJSONObject(i).optString("storedName")
                if (!safeName.matches(name)) continue
                val file = UploadedFileStore.storedFile(context, name)
                if (!file.exists() || file.length() <= 0L) continue
                zip.putNextEntry(ZipEntry("files/$name"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    data class Restored(
        val accountsJson: String,
        val filesJson: String,
        val newsTopics: String,
        val theme: String,
    )

    /** Reads a backup. Copies file bytes into private storage. Returns null when the zip is not a Track backup. */
    fun read(context: Context, uri: Uri): Restored? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        var manifest: JSONObject? = null
        val blobs = HashMap<String, File>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
                if (entry.name == "manifest.json") {
                    val text = zip.readBytes().toString(Charsets.UTF_8)
                    manifest = runCatching { JSONObject(text) }.getOrNull()
                } else if (entry.name.startsWith("files/") && safeName.matches(name) && !entry.isDirectory) {
                    val tmp = File(context.cacheDir, "restore-$name")
                    var size = 0L
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            size += n
                            if (size > 20L * 1024L * 1024L) break
                            out.write(buf, 0, n)
                        }
                    }
                    if (size in 1..(20L * 1024L * 1024L)) blobs[name] = tmp else tmp.delete()
                }
                zip.closeEntry()
            }
        }
        val root = manifest ?: return null
        if (root.optInt("version") != 1) return null
        val accounts = root.optJSONArray("accounts") ?: JSONArray()
        val files = root.optJSONArray("files") ?: JSONArray()
        val kept = JSONArray()
        for (i in 0 until files.length()) {
            val row = files.optJSONObject(i) ?: continue
            val stored = row.optString("storedName")
            val blob = blobs[stored] ?: continue
            if (!safeName.matches(stored)) {
                blob.delete()
                continue
            }
            val dest = UploadedFileStore.storedFile(context, stored)
            dest.parentFile?.mkdirs()
            blob.copyTo(dest, overwrite = true)
            dest.setExecutable(false, false)
            blob.delete()
            kept.put(row)
        }
        blobs.values.forEach { it.delete() }
        return Restored(
            accountsJson = accounts.toString(),
            filesJson = kept.toString(),
            newsTopics = root.optString("newsTopics"),
            theme = root.optString("theme"),
        )
    }
}

package com.trackme.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream

/**
 * Builds a preview from the restored original bytes.
 * Pictures and PDF pages are drawn. Text is shown as text.
 * Programs, installers, and scripts are not opened.
 */
object FilePreviewer {
    data class Result(
        val kind: Kind,
        val image: Bitmap? = null,
        val pages: List<Bitmap> = emptyList(),
        val text: String? = null,
        val media: File? = null,
        val note: String? = null,
    )

    enum class Kind { IMAGE, PDF, TEXT, VIDEO, AUDIO, BLOCKED }

    fun prepare(context: Context, file: UploadedFile): Result {
        val ext = file.extension.lowercase()
        val out = File(File(context.cacheDir, "file_preview").apply { mkdirs() }, safeLeaf(file.id, ext))
        if (!UploadedFileStore.writeOriginal(context, file, out)) {
            out.delete()
            return Result(Kind.BLOCKED, note = "The saved file could not be verified, so it was not opened.")
        }
        val head = ByteArray(32)
        val read = FileInputStream(out).use { it.read(head) }
        val bytes = if (read > 0) head.copyOf(read) else ByteArray(0)
        if (isProgram(bytes, ext)) {
            out.delete()
            return Result(Kind.BLOCKED, note = "This file is saved. It was not opened, so nothing in it can run.")
        }
        if (isImage(bytes, ext)) {
            val bitmap = decodeImage(out)
            return if (bitmap != null) {
                Result(Kind.IMAGE, image = bitmap, note = "Picture quality of the saved file is unchanged.")
            } else {
                out.delete()
                Result(Kind.BLOCKED, note = "This picture is saved. It could not be drawn, and it was not run.")
            }
        }
        if (isPdf(bytes)) {
            val pages = renderPdf(out)
            return if (pages.isNotEmpty()) {
                Result(Kind.PDF, pages = pages, note = "Showing pages from the saved PDF. Scripts inside it are not run.")
            } else {
                out.delete()
                Result(Kind.BLOCKED, note = "This PDF is saved. Its pages could not be drawn, and it was not run.")
            }
        }
        if (isVideo(bytes, ext)) return Result(Kind.VIDEO, media = out)
        if (isAudio(bytes, ext)) return Result(Kind.AUDIO, media = out)
        val text = readText(out)
        return if (text != null) {
            out.delete()
            Result(
                Kind.TEXT,
                text = text,
                note = "Shown as text. This app does not run the file.",
            )
        } else {
            out.delete()
            Result(Kind.BLOCKED, note = "This file is saved. It was not opened, so nothing in it can run.")
        }
    }

    private fun ascii(head: ByteArray, from: Int, to: Int): String {
        return String(head, from, to - from, Charsets.US_ASCII)
    }

    private fun safeLeaf(id: String, ext: String): String {
        val clean = ext.filter { it.isLetterOrDigit() }.take(8)
        return if (clean.isBlank()) "$id.bin" else "$id.$clean"
    }

    private fun isProgram(head: ByteArray, ext: String): Boolean {
        val blocked = setOf("exe", "dll", "apk", "dex", "jar", "bat", "cmd", "com", "scr", "msi", "ps1", "vbs", "sh", "so", "elf")
        if (ext in blocked) return true
        if (head.size >= 4 && head[0] == 0x7F.toByte() && head[1] == 'E'.code.toByte() && head[2] == 'L'.code.toByte() && head[3] == 'F'.code.toByte()) return true
        if (head.size >= 2 && head[0] == 'M'.code.toByte() && head[1] == 'Z'.code.toByte()) return true
        return false
    }

    private fun isPdf(head: ByteArray): Boolean {
        val mark = "%PDF-".toByteArray(Charsets.US_ASCII)
        return head.size >= mark.size && head.copyOfRange(0, mark.size).contentEquals(mark)
    }

    private fun isImage(head: ByteArray, ext: String): Boolean {
        if (ext == "svg") return false
        if (head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()) return true
        if (head.size >= 4 && head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() && head[2] == 'N'.code.toByte() && head[3] == 'G'.code.toByte()) return true
        if (head.size >= 4 && head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() && head[2] == 'F'.code.toByte()) return true
        if (head.size >= 12 && ascii(head, 0, 4) == "RIFF" && ascii(head, 8, 12) == "WEBP") return true
        if (head.size >= 2 && head[0] == 'B'.code.toByte() && head[1] == 'M'.code.toByte()) return true
        return ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    }

    private fun isVideo(head: ByteArray, ext: String): Boolean {
        val ftyp = head.size >= 8 && ascii(head, 4, 8) == "ftyp"
        val webm = head.size >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte()
        return (ftyp || webm) && ext in setOf("mp4", "m4v", "mov", "webm", "mkv", "3gp")
    }

    private fun isAudio(head: ByteArray, ext: String): Boolean {
        val id3 = head.size >= 3 && ascii(head, 0, 3) == "ID3"
        val wav = head.size >= 12 && ascii(head, 0, 4) == "RIFF" && ascii(head, 8, 12) == "WAVE"
        val ogg = head.size >= 4 && ascii(head, 0, 4) == "OggS"
        return (id3 || wav || ogg || ext in setOf("mp3", "wav", "ogg", "m4a", "aac")) &&
            ext in setOf("mp3", "wav", "ogg", "m4a", "aac")
    }

    private fun decodeImage(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun renderPdf(file: File): List<Bitmap> {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { renderer ->
                val pages = ArrayList<Bitmap>()
                val count = minOf(renderer.pageCount, 4)
                for (i in 0 until count) {
                    renderer.openPage(i).use { page ->
                        val width = 800
                        val height = (page.height * (width.toFloat() / page.width)).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages.add(bitmap)
                    }
                }
                pages
            }.also { pfd.close() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun readText(file: File): String? {
        val max = 80_000
        val raw = ByteArray(max)
        val n = FileInputStream(file).use { it.read(raw) }
        if (n <= 0) return null
        val sample = raw.copyOf(n)
        if (sample.any { it == 0.toByte() }) return null
        val bad = sample.count { b ->
            val c = b.toInt() and 0xFF
            c < 9 || (c > 13 && c < 32)
        }
        if (bad * 20 >= sample.size) return null
        return String(sample, Charsets.UTF_8).trim().ifBlank { null }
    }
}

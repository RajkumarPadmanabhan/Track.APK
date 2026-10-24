package com.trackme.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream

class VehiclePaperException(message: String) : Exception(message)

/** Text read from one pollution, insurance, or RC file. Only values present in the file are filled. */
data class PaperRead(
    val plate: String?,
    val model: String?,
    val fuel: String?,
    val insuranceOn: String?,
    val pollutionOn: String?,
    val rcOn: String?,
    val facts: List<String>,
    val file: File,
    val related: Boolean,
)

object VehiclePapers {
    private const val MAX_BYTES = 12L * 1024L * 1024L
    private const val MAX_OCR_PAGES = 10

    fun read(context: Context, uri: Uri, kind: String): PaperRead {
        val app = context.applicationContext
        val copied = copyToCache(app, uri)
        try {
            val text = when {
                isPdf(copied) -> readPdf(app, copied)
                else -> readImage(copied)
            }
            if (text.count { it.isLetterOrDigit() } < 8) {
                throw VehiclePaperException("No readable text in that file. Try a clearer photo or PDF.")
            }
            return extract(text, kind, copied)
        } catch (e: VehiclePaperException) {
            copied.delete()
            throw e
        } catch (_: OutOfMemoryError) {
            copied.delete()
            throw VehiclePaperException("That file is too large to read on this phone.")
        } catch (e: Exception) {
            copied.delete()
            throw VehiclePaperException(e.message ?: "Couldn't read that file.")
        }
    }

    fun adopt(context: Context, id: String, kind: String, temp: File, previousName: String): String {
        val dir = File(context.filesDir, "vehicle-papers/$id").apply { mkdirs() }
        val ext = when {
            temp.name.endsWith(".pdf", true) || isPdf(temp) -> "pdf"
            temp.name.endsWith(".png", true) -> "png"
            temp.name.endsWith(".webp", true) -> "webp"
            else -> "jpg"
        }
        val dest = File(dir, "$kind.$ext")
        if (previousName.isNotBlank()) {
            val old = File(dir, previousName)
            if (old.absolutePath != dest.absolutePath) old.delete()
        }
        temp.copyTo(dest, overwrite = true)
        temp.delete()
        return dest.name
    }

    fun deleteStored(context: Context, id: String) {
        File(context.filesDir, "vehicle-papers/$id").deleteRecursively()
    }

    private fun extract(text: String, kind: String, file: File): PaperRead {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val plate = VehicleBook.findPlate(text)
        var model: String? = null
        var maker: String? = null
        var fuel: String? = null
        val insuranceDates = mutableListOf<PaperDay>()
        val pollutionDates = mutableListOf<PaperDay>()
        val rcDates = mutableListOf<PaperDay>()
        val facts = mutableListOf<String>()

        for (i in lines.indices) {
            val window = lines[i] + " " + lines.getOrNull(i + 1).orEmpty()
            val lower = window.lowercase()
            val date = endDate(window)
            val pollution = listOf("pollut", "puc", "emission").any { it in lower }
            val insurance = listOf("insurance", "policy").any { it in lower }
            val fitness = listOf("fitness", "regn upto", "registration valid", "rc valid").any { it in lower }
            if (date != null) {
                when {
                    pollution -> pollutionDates += date
                    insurance -> insuranceDates += date
                    fitness -> rcDates += date
                    kind == "pollution" && isEnd(lower) -> pollutionDates += date
                    kind == "insurance" && isEnd(lower) -> insuranceDates += date
                    kind == "rc" && isEnd(lower) && !isStart(lower) -> rcDates += date
                }
            }
            labeled(lines, i, listOf("owner name", "registered owner", "owner"))?.let { facts += "Owner: $it" }
            labeled(lines, i, listOf("chassis"))?.let { facts += "Chassis: $it" }
            labeled(lines, i, listOf("engine"))?.let { facts += "Engine: $it" }
            labeled(lines, i, listOf("maker", "manufacturer"))?.let { maker = it }
            labeled(lines, i, listOf("model"))?.let { model = it }
            labeled(lines, i, listOf("fuel"))?.let { fuel = fuelWord(it) ?: it }
            labeled(lines, i, listOf("vehicle class", "vch class"))?.let { facts += "Class: $it" }
            labeled(lines, i, listOf("colour", "color"))?.let { facts += "Color: $it" }
            labeled(lines, i, listOf("insurance company", "insurer"))?.let { facts += "Insurer: $it" }
            labeled(lines, i, listOf("policy no", "policy number"))?.let { facts += "Policy: $it" }
        }

        if (kind == "pollution" && pollutionDates.isEmpty()) {
            latestUnlabeled(text)?.let { pollutionDates += it }
        }
        if (kind == "insurance" && insuranceDates.isEmpty()) {
            latestUnlabeled(text)?.let { insuranceDates += it }
        }

        val modelText = listOfNotNull(maker, model).distinct().joinToString(" ").ifBlank { null }
        fuel = fuel?.let { fuelWord(it) ?: it.take(24) }
        if (plate != null) facts += "Plate: $plate"
        if (modelText != null) facts += "Model: $modelText"
        if (!fuel.isNullOrBlank()) facts += "Fuel: $fuel"
        pollutionDates.maxOrNull()?.let { facts += "Pollution test valid until: ${it.label()}" }
        insuranceDates.maxOrNull()?.let { facts += "Insurance valid until: ${it.label()}" }
        rcDates.maxOrNull()?.let { facts += "RC valid until: ${it.label()}" }

        val shown = if (facts.isEmpty()) {
            lines.filter { it.length in 4..80 }.take(8)
        } else {
            facts.distinct().take(16)
        }
        return PaperRead(
            plate = plate,
            model = modelText?.take(40),
            fuel = fuel?.take(24),
            insuranceOn = insuranceDates.maxOrNull()?.label(),
            pollutionOn = pollutionDates.maxOrNull()?.label(),
            rcOn = rcDates.maxOrNull()?.label(),
            facts = shown,
            file = file,
            related = looksLikeVehicle(text, plate != null),
        )
    }

    private fun looksLikeVehicle(text: String, hasPlate: Boolean): Boolean {
        val lower = text.lowercase()
        val keys = listOf(
            "vehicle", "registration", "regn", "insurance", "policy", "pollution",
            "puc", "pucc", "chassis", "fitness", "fuel", "emission", "engine", "maker",
        )
        val hits = keys.count { it in lower }
        return hits >= 2 || (hasPlate && hits >= 1)
    }

    private fun latestUnlabeled(text: String): PaperDay? {
        val dates = VehicleBook.findDays(text)
        if (dates.size == 1) return dates.first()
        return dates.maxOrNull()
    }

    private fun endDate(window: String): PaperDay? {
        val dates = VehicleBook.findDays(window)
        if (dates.isEmpty()) return null
        val lower = window.lowercase()
        if (isStart(lower) && !isEnd(lower)) return null
        return dates.last()
    }

    private fun isEnd(lower: String): Boolean {
        if (lower.trim().startsWith("to ")) return true
        if (Regex("""\btill\b""").containsMatchIn(lower)) return true
        return listOf("valid till", "valid upto", "valid up to", "upto", "up to", "expiry", "expires", "expire", "until", "fitness").any { it in lower }
    }

    private fun isStart(lower: String): Boolean {
        if (lower.trim().startsWith("from ")) return true
        return listOf("date of issue", "issued", "issue date", "w.e.f", "wef", "mfg", "manufactur", "regn dt", "regn date", "registration date", "from ").any { it in lower }
    }

    private fun labeled(lines: List<String>, index: Int, keys: List<String>): String? {
        val line = lines[index]
        val lower = line.lowercase()
        val key = keys.firstOrNull { lower == it || lower.startsWith(it) } ?: return null
        val same = line.substring(key.length).trim(' ', ':', '-', '.', '#').trim()
        val value = when {
            same.length >= 2 && !isLabel(same) -> same
            else -> lines.getOrNull(index + 1)?.takeIf { it.length >= 2 && !isLabel(it) }
        } ?: return null
        if (value.count { it == ' ' } > 6) return null
        return value.take(60)
    }

    private fun isLabel(value: String): Boolean {
        val lower = value.lowercase()
        val keys = listOf(
            "owner", "chassis", "engine", "maker", "manufacturer", "model", "fuel",
            "vehicle class", "colour", "color", "insurance", "policy", "regn", "registration",
            "valid", "fitness", "puc", "pollution",
        )
        return keys.any { lower == it || lower.startsWith("$it ") || lower.startsWith("$it:") }
    }

    private fun fuelWord(raw: String): String? {
        val lower = raw.lowercase()
        val word = listOf("petrol", "diesel", "cng", "lpg", "electric", "hybrid", "battery").firstOrNull { it in lower }
        return word?.replaceFirstChar { it.uppercase() }
    }

    private fun readImage(file: File): String {
        val bitmap = decodeImage(file) ?: throw VehiclePaperException("That image could not be read. Use a JPG, PNG, or PDF.")
        return try {
            ocr(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun readPdf(context: Context, file: File): String {
        var text = ""
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(file).use { doc ->
                if (doc.isEncrypted) {
                    throw VehiclePaperException("This PDF is locked. Upload an unlocked copy.")
                }
                if (doc.numberOfPages <= 0) {
                    throw VehiclePaperException("This PDF has no pages.")
                }
                text = PDFTextStripper().apply { sortByPosition = true }.getText(doc).orEmpty()
            }
        } catch (e: VehiclePaperException) {
            throw e
        } catch (_: Exception) {
            text = ""
        }
        if (text.count { it.isLetterOrDigit() } >= 40) return text
        val ocr = ocrPdf(file)
        return if (ocr.count { it.isLetterOrDigit() } > text.count { it.isLetterOrDigit() }) ocr else text
    }

    private fun ocrPdf(file: File): String {
        val out = StringBuilder()
        try {
            android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pages = minOf(renderer.pageCount, MAX_OCR_PAGES)
                    for (i in 0 until pages) {
                        renderer.openPage(i).use { page ->
                            val scale = minOf(2f, 1600f / page.width.coerceAtLeast(1))
                            val width = maxOf((page.width * scale).toInt(), 1)
                            val height = maxOf((page.height * scale).toInt(), 1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                Canvas(bitmap).drawColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val pageText = ocr(bitmap)
                                if (pageText.isNotBlank()) {
                                    out.append(pageText)
                                    out.append('\n')
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            return out.toString()
        }
        return out.toString()
    }

    private fun ocr(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text.orEmpty()
        } finally {
            runCatching { recognizer.close() }
        }
    }

    private fun decodeImage(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) sample *= 2
        val raw = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val rotation = rotationOf(file)
        if (rotation == 0) return raw
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val turned = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (turned != raw) raw.recycle()
        return turned
    }

    private fun rotationOf(file: File): Int {
        return try {
            val orientation = ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun isPdf(file: File): Boolean {
        val header = ByteArray(5)
        val read = file.inputStream().use { it.read(header) }
        return read == 5 && String(header) == "%PDF-"
    }

    private fun copyToCache(context: Context, uri: Uri): File {
        val out = File(context.cacheDir, "vehicle_${System.currentTimeMillis()}")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw VehiclePaperException("Couldn't open that file.")
        var total = 0L
        try {
            input.use { stream ->
                FileOutputStream(out).use { sink ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw VehiclePaperException("That file is larger than 12 MB.")
                        sink.write(buffer, 0, n)
                    }
                }
            }
        } catch (e: VehiclePaperException) {
            out.delete()
            throw e
        }
        if (out.length() <= 0L) {
            out.delete()
            throw VehiclePaperException("That file is empty.")
        }
        val named = when {
            isPdf(out) -> File(out.parentFile, out.name + ".pdf")
            else -> out
        }
        if (named != out) out.renameTo(named)
        return if (named.exists()) named else out
    }
}

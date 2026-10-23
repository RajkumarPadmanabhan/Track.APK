package com.trackme.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.GZIPOutputStream

class PdfReadException(message: String) : Exception(message)

/**
 * Reads every page of an uploaded PDF on this device and builds a simple portfolio.
 * The portfolio uses only words that are in the PDF. Nothing is guessed or added.
 * Pages with no text layer (a scanned resume) are read with OCR instead.
 */
object PdfPortfolioReader {

    suspend fun read(context: Context, uri: Uri): PdfPortfolio = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val fileName = displayName(appContext, uri) ?: "Resume.pdf"
        if (!fileName.endsWith(".pdf", ignoreCase = true)) {
            throw PdfReadException("Only .pdf files are accepted.")
        }
        val file = copyToCache(appContext, uri, fileName)
        try {
            checkHeader(file)
            checkForActiveContent(file)

            var usedOcr = false
            var pageCount = 0
            var text = ""
            try {
                PDFBoxResourceLoader.init(appContext)
                PDDocument.load(file).use { doc ->
                    if (doc.isEncrypted) {
                        throw PdfReadException("Password-protected PDFs cannot be read. Upload an unlocked copy.")
                    }
                    pageCount = doc.numberOfPages
                    if (pageCount <= 0) {
                        throw PdfReadException("This file looks like a PDF but has no pages, so it was rejected.")
                    }
                    val stripper = PDFTextStripper().apply { sortByPosition = true }
                    text = stripper.getText(doc).orEmpty()
                }
            } catch (e: PdfReadException) {
                throw e
            } catch (e: OutOfMemoryError) {
                throw PdfReadException("This PDF is too large to read on this phone.")
            } catch (e: Exception) {
                throw PdfReadException("This file could not be read as a real PDF, so it was rejected.")
            }

            val letters = text.count { it.isLetterOrDigit() }
            if (letters < 40 * maxOf(pageCount, 1)) {
                val ocr = ocrAllPages(appContext, uri)
                if (ocr.second.count { it.isLetterOrDigit() } > letters) {
                    text = ocr.second
                    usedOcr = true
                }
            }
            if (text.count { it.isLetterOrDigit() } < 20) {
                throw PdfReadException(
                    "No readable text was found in this PDF. If it is a photo or scan, upload a clearer PDF."
                )
            }
            val portfolio = parse(text, fileName, maxOf(pageCount, 1), usedOcr)
            val stored = storeCompressed(appContext, file)
            portfolio.copy(
                uploadedFileId = stored.first,
                originalSize = file.length(),
                storedSize = stored.second,
                sha256 = sha256Hex(file),
            )
        } finally {
            runCatching { file.delete() }
        }
    }

    /** Every real PDF starts with these five bytes. Anything else is rejected first. */
    private fun checkHeader(file: File) {
        val header = ByteArray(5)
        val read = FileInputStream(file).use { it.read(header) }
        val magic = if (read == 5) String(header, StandardCharsets.US_ASCII) else ""
        if (magic != "%PDF-") {
            throw PdfReadException("This is not a PDF file. Only real PDF files are accepted.")
        }
    }

    /** Scripts and auto-run actions are rejected, so a dressed-up file cannot act on open. */
    private fun checkForActiveContent(file: File) {
        val raw = String(file.readBytes(), StandardCharsets.ISO_8859_1)
        val banned = listOf("/JS", "/JavaScript", "/Launch", "/OpenAction", "/EmbeddedFile", "/RichMedia")
        if (banned.any { it in raw }) {
            throw PdfReadException(
                "This PDF contains scripts or auto-open actions, so it was rejected. Upload a plain resume PDF."
            )
        }
    }

    /** Lossless copy in the app's private folder, under a generated name. */
    private fun storeCompressed(context: Context, original: File): Pair<String, Long> {
        val id = UUID.randomUUID().toString()
        val dir = UploadedFileStore.dir(context)
        val stored = File(dir, "$id.pdf.gz")
        FileInputStream(original).use { input ->
            GZIPOutputStream(FileOutputStream(stored)).use { gz -> input.copyTo(gz) }
        }
        return id to stored.length()
    }

    private fun displayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun copyToCache(context: Context, uri: Uri, fileName: String): File {
        val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "resume.pdf" }
        val out = File(context.cacheDir, "pdf_${System.currentTimeMillis()}_$safe")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw PdfReadException("Couldn't open this PDF.")
        var total = 0L
        try {
            input.use { stream ->
                FileOutputStream(out).use { sink ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > UploadedFileStore.maxBytes()) {
                            throw PdfReadException("This PDF is larger than ${UploadedFileStore.MAX_LABEL}. Upload a smaller file.")
                        }
                        sink.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: PdfReadException) {
            runCatching { out.delete() }
            throw e
        }
        if (out.length() <= 0L) throw PdfReadException("This PDF file is empty.")
        return out
    }

    /** Renders every page and reads its text. Used only when the PDF has no text layer. */
    private fun ocrAllPages(context: Context, uri: Uri): Pair<Int, String> {
        val out = StringBuilder()
        var pages = 0
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0 to ""
            pfd.use {
                PdfRenderer(it).use { renderer ->
                    pages = renderer.pageCount
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val scale = 2
                            val width = maxOf(page.width * scale, 1)
                            val height = maxOf(page.height * scale, 1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                Canvas(bitmap).drawColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                                val pageText = result.text.orEmpty()
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
            return pages to out.toString()
        } finally {
            runCatching { recognizer.close() }
        }
        return pages to out.toString()
    }

    // ---------- Turning the document text into headed portfolio sections ----------

    private val headings: Map<String, Pair<String, String>> = buildMap {
        fun add(title: String, category: String, vararg keys: String) {
            for (k in keys) put(k, title to category)
        }
        add("Summary", "Profile", "summary", "profile summary", "professional summary", "profile", "about", "about me", "objective", "career objective", "overview")
        add("Experience", "Experience", "experience", "work experience", "professional experience", "employment", "employment history", "work history", "career history")
        add("Internship", "Experience", "internship", "internships", "training", "trainings")
        add("Education", "Education", "education", "educational qualification", "educational qualifications", "academic qualification", "academic qualifications", "academics", "academic background")
        add("Skills", "Skills", "skills", "technical skills", "core skills", "key skills", "core competencies", "competencies", "technologies", "tech stack", "tools", "tools and technologies")
        add("Projects", "Projects", "projects", "project", "project experience", "personal projects", "academic projects", "key projects")
        add("Certifications", "Certifications", "certifications", "certification", "certificates", "courses", "courses and certifications")
        add("Achievements", "Achievements", "achievements", "achievement", "awards", "honors", "honours", "awards and achievements", "accomplishments")
        add("Publications", "Publications", "publications", "publication", "papers", "research")
        add("Languages", "Languages", "languages", "language")
        add("Volunteering", "Volunteering", "volunteering", "volunteer experience", "social work")
        add("Interests", "Interests", "interests", "hobbies", "hobbies and interests", "activities")
        add("Contact", "Contact", "contact", "contact details", "contact information", "personal details")
    }

    private fun headingOf(line: String): Pair<String, String>? {
        var t = line.trim().trimEnd(':').trim()
        t = t.replace(Regex("^\\d+[.)]\\s*"), "")
        t = t.trim { !it.isLetterOrDigit() }
        if (t.isBlank() || t.length > 60) return null
        return headings[t.lowercase()]
    }

    private fun cleanLine(raw: String): String {
        return raw.replace('\u00a0', ' ')
            .replace(Regex("[\\t ]+"), " ")
            .trim()
            .removePrefix("•").removePrefix("·").removePrefix("▪")
            .removePrefix("-").removePrefix("*").trim()
    }

    fun parse(rawText: String, fileName: String, pageCount: Int, usedOcr: Boolean): PdfPortfolio {
        val lines = rawText.lines().map { cleanLine(it) }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            throw PdfReadException("No readable text was found in this PDF.")
        }

        val email = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
            .find(rawText)?.value
        val phone = Regex("(?:\\+?\\d[\\d\\s\\-()]{8,}\\d)")
            .findAll(rawText).map { it.value.trim() }
            .firstOrNull { candidate -> candidate.count { it.isDigit() } in 10..15 }
        val links = Regex("(?i)\\b(?:https?://|www\\.)[^\\s<>\"')]+|\\b(?:linkedin\\.com|github\\.com|gitlab\\.com)/[^\\s<>\"')]+")
            .findAll(rawText).map { it.value.trim().trimEnd('.', ',') }.distinct().take(6).toList()

        var name = ""
        var headline: String? = null
        val headerLines = ArrayList<String>()
        val sections = ArrayList<PortfolioSection>()
        var currentTitle: String? = null
        var currentCategory = "Resume"
        val currentItems = ArrayList<String>()

        fun flush() {
            val title = currentTitle ?: return
            val items = currentItems.filter { it.isNotBlank() }
            if (items.isNotEmpty()) sections += PortfolioSection(title, currentCategory, items)
            currentItems.clear()
        }

        for (line in lines) {
            val heading = headingOf(line)
            if (heading != null) {
                flush()
                currentTitle = heading.first
                currentCategory = heading.second
                continue
            }
            // A heading with its first line on the same row, e.g. "Skills: Kotlin, Java".
            val inline = Regex("^([^:]{3,40}):\\s*(.+)$").find(line)
            val inlineHeading = inline?.let { headingOf(it.groupValues[1]) }
            if (inline != null && inlineHeading != null) {
                flush()
                currentTitle = inlineHeading.first
                currentCategory = inlineHeading.second
                currentItems += inline.groupValues[2].trim()
                continue
            }
            if (currentTitle == null) {
                headerLines += line
                continue
            }
            val last = currentItems.lastOrNull()
            val continues = last != null &&
                !last.endsWith(".") && !last.endsWith(";") && !last.endsWith(":") &&
                line.firstOrNull()?.isLowerCase() == true
            if (continues) {
                currentItems[currentItems.lastIndex] = "$last $line"
            } else {
                currentItems += line
            }
        }
        flush()

        // The block before the first heading holds the name, role, and contact lines.
        val contactNoise = Regex("(?i)resume|curriculum vitae|^cv$")
        val meaningfulHeader = headerLines.filter { !contactNoise.matches(it.trim()) }
        if (meaningfulHeader.isNotEmpty()) {
            name = meaningfulHeader.first()
            val rest = meaningfulHeader.drop(1).filter { line ->
                line != email && (phone == null || !line.contains(phone)) && links.none { line.contains(it) }
            }
            headline = rest.firstOrNull { it.length in 3..90 }
            val extra = rest.drop(if (headline != null) 1 else 0)
            if (extra.isNotEmpty()) {
                val exists = sections.any { it.category == "Profile" }
                if (!exists) {
                    sections.add(0, PortfolioSection("Summary", "Profile", extra))
                }
            }
        }
        if (name.isBlank()) {
            name = sections.firstOrNull()?.items?.firstOrNull()?.take(60)
                ?: fileName.removeSuffix(".pdf").replace('_', ' ').replace('-', ' ').trim().ifBlank { "My Portfolio" }
        }
        if (sections.isEmpty()) {
            sections += PortfolioSection("Resume", "Resume", lines.take(120))
        }

        val skills = sections.firstOrNull { it.category == "Skills" }?.items
            ?.flatMap { it.split(Regex("[,|/•]")) }
            ?.map { it.trim() }
            ?.filter { it.length in 1..40 }
            ?.distinctBy { it.lowercase() }
            ?.take(30)
            .orEmpty()

        val summary = sections.firstOrNull { it.category == "Profile" }
            ?.items?.joinToString(" ")?.take(600)

        return PdfPortfolio(
            fileName = fileName,
            pageCount = pageCount,
            name = name.take(80),
            headline = headline?.take(90),
            email = email,
            phone = phone,
            links = links,
            summary = summary,
            sections = sections,
            skills = skills,
            usedOcr = usedOcr,
        )
    }
}

package com.trackme.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StoryGenerator {
    data class StoryMetrics(
        val userName: String,
        val totalReach: String,
        val gitEventsToday: String,
        val youTubeSubs: String,
        val linkedInFollowers: String
    )

    fun createAndShareStory(context: Context, metrics: StoryMetrics) {
        val bitmap = generateStoryBitmap(metrics)
        shareStoryBitmap(context, bitmap)
    }

    private fun generateStoryBitmap(metrics: StoryMetrics): Bitmap {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // 1. Dark Gradient Background
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.parseColor("#121212"),
                Color.parseColor("#1E1E2C"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        // Text Paints
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        // 2. Header & Branding
        textPaint.textSize = 42f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.parseColor("#FF8A80")
        canvas.drawText("TRACKME DAILY RECAP", width / 2f, 220f, textPaint)
        val currentDate = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(Date())
        textPaint.textSize = 30f
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = Color.parseColor("#AAAAAA")
        canvas.drawText(currentDate, width / 2f, 280f, textPaint)
        // 3. User Handle
        textPaint.textSize = 50f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.WHITE
        canvas.drawText("@${metrics.userName}", width / 2f, 380f, textPaint)
        // 4. Large Total Reach Display Box
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#262837")
        }
        val mainCardRect = RectF(100f, 440f, 980f, 740f)
        canvas.drawRoundRect(mainCardRect, 32f, 32f, cardPaint)
        textPaint.textSize = 34f
        textPaint.color = Color.parseColor("#80CBC4")
        canvas.drawText("TOTAL AUDIENCE REACH", width / 2f, 530f, textPaint)
        textPaint.textSize = 100f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.WHITE
        canvas.drawText(metrics.totalReach, width / 2f, 650f, textPaint)
        // 5. Grid Metric Cards (YouTube & LinkedIn)
        val cardWidth = 420f
        val cardHeight = 260f
        // YouTube Card
        val ytRect = RectF(100f, 800f, 100f + cardWidth, 800f + cardHeight)
        cardPaint.color = Color.parseColor("#FFCDD2") // Soft Red
        canvas.drawRoundRect(ytRect, 28f, 28f, cardPaint)
        textPaint.textSize = 32f
        textPaint.color = Color.parseColor("#C62828")
        canvas.drawText("YouTube Subs", ytRect.centerX(), 870f, textPaint)
        textPaint.textSize = 65f
        textPaint.color = Color.BLACK
        canvas.drawText(metrics.youTubeSubs, ytRect.centerX(), 960f, textPaint)
        // LinkedIn Card
        val liRect = RectF(560f, 800f, 560f + cardWidth, 800f + cardHeight)
        cardPaint.color = Color.parseColor("#BBDEFB") // Soft Blue
        canvas.drawRoundRect(liRect, 28f, 28f, cardPaint)
        textPaint.textSize = 32f
        textPaint.color = Color.parseColor("#1565C0")
        canvas.drawText("LinkedIn", liRect.centerX(), 870f, textPaint)
        textPaint.textSize = 65f
        textPaint.color = Color.BLACK
        canvas.drawText(metrics.linkedInFollowers, liRect.centerX(), 960f, textPaint)
        // 6. Git Activity Banner
        val gitRect = RectF(100f, 1120f, 980f, 1360f)
        cardPaint.color = Color.parseColor("#C8E6C9") // Soft Green
        canvas.drawRoundRect(gitRect, 28f, 28f, cardPaint)
        textPaint.textSize = 34f
        textPaint.color = Color.parseColor("#2E7D32")
        canvas.drawText("Git Commits & Events Today", width / 2f, 1200f, textPaint)
        textPaint.textSize = 80f
        textPaint.color = Color.parseColor("#1B5E20")
        canvas.drawText("${metrics.gitEventsToday} Contributions", width / 2f, 1300f, textPaint)
        // 7. Footer
        textPaint.textSize = 28f
        textPaint.color = Color.parseColor("#777777")
        canvas.drawText("Powered by TrackMe App 🚀", width / 2f, 1800f, textPaint)
        return bitmap
    }

    private fun shareStoryBitmap(context: Context, bitmap: Bitmap) {
        val cacheFolder = File(context.cacheDir, "stories").apply { mkdirs() }
        val storyFile = File(cacheFolder, "daily_recap_${System.currentTimeMillis()}.png")
        FileOutputStream(storyFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            storyFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, "Here is my daily stats recap! #TrackMe")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Daily Recap Story").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

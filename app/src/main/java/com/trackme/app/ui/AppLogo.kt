package com.trackme.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.data.Platform

enum class AppLogoKind {
    INSTAGRAM, LINKEDIN, GITHUB, GITLAB, YOUTUBE, FACEBOOK, X,
    PDF, WORD, EXCEL, SLIDES, IMAGE, VIDEO, WEB,
}

fun appLogoKind(url: String, platform: Platform): AppLogoKind {
    val path = url.lowercase().substringBefore('?').substringBefore('#')
    val ext = path.substringAfterLast('.', "")
    when (ext) {
        "pdf" -> return AppLogoKind.PDF
        "doc", "docx" -> return AppLogoKind.WORD
        "xls", "xlsx", "csv" -> return AppLogoKind.EXCEL
        "ppt", "pptx" -> return AppLogoKind.SLIDES
        "png", "jpg", "jpeg", "gif", "webp", "bmp" -> return AppLogoKind.IMAGE
        "mp4", "mov", "webm", "mkv" -> return AppLogoKind.VIDEO
    }
    val host = path.substringAfter("://").removePrefix("www.").substringBefore('/')
    return when {
        "instagram.com" in host -> AppLogoKind.INSTAGRAM
        "linkedin.com" in host -> AppLogoKind.LINKEDIN
        "github.com" in host -> AppLogoKind.GITHUB
        "gitlab.com" in host -> AppLogoKind.GITLAB
        "youtube.com" in host || "youtu.be" in host -> AppLogoKind.YOUTUBE
        "facebook.com" in host || host.startsWith("fb.com") -> AppLogoKind.FACEBOOK
        host == "x.com" || "twitter.com" in host -> AppLogoKind.X
        else -> when (platform) {
            Platform.INSTAGRAM -> AppLogoKind.INSTAGRAM
            Platform.LINKEDIN -> AppLogoKind.LINKEDIN
            Platform.GITHUB -> AppLogoKind.GITHUB
            Platform.GITLAB -> AppLogoKind.GITLAB
            Platform.YOUTUBE -> AppLogoKind.YOUTUBE
            Platform.FACEBOOK -> AppLogoKind.FACEBOOK
            Platform.X -> AppLogoKind.X
            else -> AppLogoKind.WEB
        }
    }
}

@Composable
fun AppLogo(kind: AppLogoKind, size: Dp = 62.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        when (kind) {
            AppLogoKind.INSTAGRAM -> InstagramMark()
            AppLogoKind.LINKEDIN -> LetterTile(Color(0xFF0A66C2), "in", 22)
            AppLogoKind.GITHUB -> GitMark(Color(0xFF24292F))
            AppLogoKind.GITLAB -> GitMark(Color(0xFFE24329))
            AppLogoKind.YOUTUBE -> PlayMark(Color(0xFFFF0033))
            AppLogoKind.FACEBOOK -> LetterTile(Color(0xFF1877F2), "f", 28)
            AppLogoKind.X -> LetterTile(Color(0xFF111111), "X", 26)
            AppLogoKind.PDF -> DocTile(Color(0xFFE53935), "PDF")
            AppLogoKind.WORD -> DocTile(Color(0xFF2B579A), "W")
            AppLogoKind.EXCEL -> DocTile(Color(0xFF217346), "X")
            AppLogoKind.SLIDES -> DocTile(Color(0xFFD24726), "P")
            AppLogoKind.IMAGE -> ImageMark()
            AppLogoKind.VIDEO -> PlayMark(Color(0xFF111111))
            AppLogoKind.WEB -> LetterTile(Color(0xFF0F766E), "W", 26)
        }
    }
}

@Composable
private fun InstagramMark() {
    Canvas(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF))),
        ),
    ) {
        val s = size.minDimension
        val inset = s * 0.22f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(inset, inset),
            size = Size(s - inset * 2, s - inset * 2),
            cornerRadius = CornerRadius(s * 0.18f, s * 0.18f),
            style = Stroke(width = s * 0.055f),
        )
        drawCircle(Color.White, radius = s * 0.13f, style = Stroke(width = s * 0.055f))
        drawCircle(Color.White, radius = s * 0.035f, center = Offset(s * 0.68f, s * 0.32f))
    }
}

@Composable
private fun PlayMark(color: Color) {
    Canvas(Modifier.fillMaxSize().background(color)) {
        val s = size.minDimension
        val path = Path().apply {
            moveTo(s * 0.40f, s * 0.32f)
            lineTo(s * 0.40f, s * 0.68f)
            lineTo(s * 0.68f, s * 0.50f)
            close()
        }
        drawPath(path, Color.White)
    }
}

@Composable
private fun GitMark(color: Color) {
    Canvas(Modifier.fillMaxSize().background(color)) {
        val s = size.minDimension
        val r = s * 0.075f
        drawCircle(Color.White, r, Offset(s * 0.32f, s * 0.30f))
        drawCircle(Color.White, r, Offset(s * 0.68f, s * 0.30f))
        drawCircle(Color.White, r, Offset(s * 0.50f, s * 0.70f))
        drawLine(Color.White, Offset(s * 0.32f, s * 0.30f), Offset(s * 0.68f, s * 0.30f), s * 0.045f, StrokeCap.Round)
        drawLine(Color.White, Offset(s * 0.50f, s * 0.30f), Offset(s * 0.50f, s * 0.70f), s * 0.045f, StrokeCap.Round)
    }
}

@Composable
private fun ImageMark() {
    Canvas(Modifier.fillMaxSize().background(Color(0xFF7C3AED))) {
        val s = size.minDimension
        drawCircle(Color(0xFFFDE68A), s * 0.08f, Offset(s * 0.68f, s * 0.32f))
        val hill = Path().apply {
            moveTo(s * 0.16f, s * 0.74f)
            lineTo(s * 0.40f, s * 0.46f)
            lineTo(s * 0.54f, s * 0.60f)
            lineTo(s * 0.72f, s * 0.40f)
            lineTo(s * 0.86f, s * 0.74f)
            close()
        }
        drawPath(hill, Color.White)
    }
}

@Composable
private fun LetterTile(color: Color, mark: String, textSp: Int) {
    Box(Modifier.fillMaxSize().background(color), contentAlignment = Alignment.Center) {
        Text(mark, color = Color.White, fontSize = textSp.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DocTile(color: Color, mark: String) {
    Box(Modifier.fillMaxSize().background(Color(0xFFF4F4F5)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension
            drawRoundRect(
                color,
                Offset(s * 0.22f, s * 0.14f),
                Size(s * 0.56f, s * 0.72f),
                CornerRadius(s * 0.06f, s * 0.06f),
            )
        }
        Text(
            mark,
            color = Color.White,
            fontSize = if (mark.length > 1) 11.sp else 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

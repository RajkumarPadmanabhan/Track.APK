package com.trackme.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.Screen
import com.trackme.app.data.Platform

// ---------- Palette (taken from the reference designs) ----------

object Brand {
    val Coral = Color(0xFFFF4D6D)
    val Lime = Color(0xFFC8F169)
    val Sage = Color(0xFFD9DDCF)
    val Ink = Color(0xFF0E0E0E)
    val Panel = Color(0xFF161616)
    val Up = Color(0xFF1F9D55)
    val UpOnDark = Color(0xFF7CE38B)
    val Down = Color(0xFFE5484D)
}

/** Colour of a platform's card on the home page. */
val Platform.cardColor: Color
    get() = when (this) {
        Platform.GITHUB -> Color(0xFFD8CCFF)
        Platform.GITLAB -> Color(0xFFFFA574)
        Platform.INSTAGRAM -> Color(0xFFF7E04D)
        Platform.LINKEDIN -> Color(0xFF9ED8FF)
        Platform.X -> Color(0xFFCBF26E)
        Platform.YOUTUBE -> Color(0xFFFF7A85)
        Platform.FACEBOOK -> Color(0xFF8BB7F0)
        Platform.AMAZON -> Color(0xFFFFC56E)
        Platform.FLIPKART -> Color(0xFFFFE14A)
        Platform.PORTFOLIO -> Color(0xFF6EE7B7)
    }

/** Two-letter mark shown in the round badge (no trademarked logos are bundled). */
val Platform.badge: String
    get() = when (this) {
        Platform.GITHUB -> "GH"
        Platform.GITLAB -> "GL"
        Platform.INSTAGRAM -> "IG"
        Platform.LINKEDIN -> "in"
        Platform.X -> "X"
        Platform.YOUTUBE -> "YT"
        Platform.FACEBOOK -> "FB"
        Platform.AMAZON -> "AZ"
        Platform.FLIPKART -> "FK"
        Platform.PORTFOLIO -> "WEB"
    }

// ---------- Card shape with a notch in the bottom-left corner ----------

/** Rounded card with a rectangular bite taken out of the bottom-left corner. */
class NotchShape(
    private val corner: Dp,
    private val notchWidth: Dp,
    private val notchHeight: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) {
            return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, w.coerceAtLeast(0f), h.coerceAtLeast(0f)))
        }

        val c = with(density) { corner.toPx() }.coerceAtMost(minOf(w, h) / 2f)
        val minW = (c * 2f).coerceAtMost(w)
        val maxW = (w - c * 2f).coerceAtLeast(minW)
        val nw = with(density) { notchWidth.toPx() }.coerceIn(minW, maxW)

        val minH = c.coerceAtMost(h)
        val maxH = (h - c * 2f).coerceAtLeast(minH)
        val nh = with(density) { notchHeight.toPx() }.coerceIn(minH, maxH)

        val f = c * 0.7f
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo((w - c).coerceAtLeast(c), 0f)
            quadraticBezierTo(w, 0f, w, c)
            lineTo(w, (h - c).coerceAtLeast(c))
            quadraticBezierTo(w, h, (w - c).coerceAtLeast(c), h)
            lineTo((nw + f).coerceAtMost(w), h)
            quadraticBezierTo(nw, h, nw, (h - f).coerceAtLeast(0f))
            lineTo(nw, (h - nh + f).coerceIn(0f, h))
            quadraticBezierTo(nw, (h - nh).coerceIn(0f, h), (nw - f).coerceAtLeast(0f), (h - nh).coerceIn(0f, h))
            lineTo(c, (h - nh).coerceIn(0f, h))
            quadraticBezierTo(0f, (h - nh).coerceIn(0f, h), 0f, (h - nh - c).coerceIn(0f, h))
            lineTo(0f, c)
            quadraticBezierTo(0f, 0f, c, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

// ---------- Small visuals ----------

/**
 * Vertical rounded bars, centred (like the audio waveform in the "Statistic" reference).
 * Values are 0..1; 0 draws a small dim stub.
 */
@Composable
fun WaveformBars(
    values: List<Float>,
    modifier: Modifier,
    color: Color,
    dim: Color,
    gap: Dp = 3.dp,
) {
    var touchedIndex by remember { mutableStateOf<Int?>(null) }
    val animatedTouchIndex by animateFloatAsState(
        targetValue = touchedIndex?.toFloat() ?: -10f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "touchWave"
    )

    Canvas(
        modifier = modifier
            .pointerInput(values) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val n = values.size
                        if (n > 0) {
                            val g = gap.toPx()
                            val bw = ((size.width - g * (n - 1)) / n).coerceAtLeast(1f)
                            val idx = (offset.x / (bw + g)).toInt().coerceIn(0, n - 1)
                            touchedIndex = idx
                        }
                    },
                    onDragEnd = { touchedIndex = null },
                    onDragCancel = { touchedIndex = null },
                    onDrag = { change, _ ->
                        val n = values.size
                        if (n > 0) {
                            val g = gap.toPx()
                            val bw = ((size.width - g * (n - 1)) / n).coerceAtLeast(1f)
                            val idx = (change.position.x / (bw + g)).toInt().coerceIn(0, n - 1)
                            touchedIndex = idx
                        }
                    }
                )
            }
            .pointerInput(values) {
                detectTapGestures(
                    onPress = { offset ->
                        val n = values.size
                        if (n > 0) {
                            val g = gap.toPx()
                            val bw = ((size.width - g * (n - 1)) / n).coerceAtLeast(1f)
                            val idx = (offset.x / (bw + g)).toInt().coerceIn(0, n - 1)
                            touchedIndex = idx
                            tryAwaitRelease()
                            touchedIndex = null
                        }
                    }
                )
            }
    ) {
        val n = values.size
        if (n == 0) return@Canvas
        val g = gap.toPx()
        val bw = ((size.width - g * (n - 1)) / n).coerceAtLeast(1f)
        val activeTouch = animatedTouchIndex

        values.forEachIndexed { i, raw ->
            val v = raw.coerceIn(0f, 1f)
            val dist = kotlin.math.abs(i - activeTouch)
            val waveMultiplier = if (dist < 4.5f) {
                1.0f + 0.65f * (1.0f - (dist / 4.5f))
            } else {
                1.0f
            }

            val baseH = if (v <= 0f) size.height * 0.12f else size.height * (0.22f + 0.78f * v)
            val h = (baseH * waveMultiplier).coerceAtMost(size.height)
            val isHighlighted = dist < 1.2f

            drawRoundRect(
                color = when {
                    isHighlighted -> color
                    v <= 0f -> dim
                    else -> color.copy(alpha = (0.7f + 0.3f * (1f - (dist / 5f).coerceAtMost(1f))))
                },
                topLeft = Offset(i * (bw + g), (size.height - h) / 2f),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f),
            )
        }
    }
}

/** A smooth line of follower or subscriber totals. A steady count sits high, not as a dashed stub. */
@Composable
fun CountChart(values: List<Float>, modifier: Modifier, color: Color) {
    Canvas(modifier) {
        if (values.size < 2 || values.all { it <= 0f }) return@Canvas
        val min = values.min()
        val max = values.max()
        val points = values.mapIndexed { i, v ->
            val x = i * size.width / (values.lastIndex).coerceAtLeast(1)
            val t = if (max == min) 0.86f else ((v - min) / (max - min)).coerceIn(0f, 1f)
            val y = size.height * (1f - (0.10f + 0.78f * t))
            Offset(x, y)
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val cur = points[i]
                val cx = (prev.x + cur.x) / 2f
                quadraticBezierTo(cx, prev.y, cx, (prev.y + cur.y) / 2f)
                quadraticBezierTo(cx, cur.y, cur.x, cur.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(fill, color.copy(alpha = 0.16f))
        drawPath(
            line,
            color,
            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
fun RemoteAvatar(url: String, size: Int = 72) {
    val context = LocalContext.current
    var bmp by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isBlank()) {
            bmp = null
            return@LaunchedEffect
        }
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> {
                        URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                    }
                    url.startsWith("content://", ignoreCase = true) -> {
                        context.contentResolver.openInputStream(Uri.parse(url))?.use {
                            BitmapFactory.decodeStream(it)
                        }
                    }
                    else -> {
                        val f = java.io.File(url)
                        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                    }
                }
            }.getOrNull()
        }
    }
    val mod = Modifier.size(size.dp).clip(CircleShape)
    val imageBitmap = remember(bmp) {
        bmp?.let {
            try { it.asImageBitmap() } catch (e: Exception) { null }
        }
    }
    if (imageBitmap != null) {
        Image(imageBitmap, contentDescription = "avatar", modifier = mod, contentScale = ContentScale.Crop)
    } else {
        Box(mod.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/** Round avatar showing the first letter of a name. */
@Composable
fun InitialAvatar(name: String, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val base = modifier.size(size).clip(CircleShape).background(Brand.Lime)
    Box(
        if (onClick != null) base.clickable { onClick() } else base,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifBlank { "?" },
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/** User avatar displaying a photo if available, or initial letter otherwise. */
@Composable
fun UserAvatar(name: String, avatarUrl: String?, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    if (!avatarUrl.isNullOrBlank()) {
        val base = modifier.size(size).clip(CircleShape)
        val mod = if (onClick != null) base.clickable { onClick() } else base
        Box(mod, contentAlignment = Alignment.Center) {
            RemoteAvatar(url = avatarUrl, size = size.value.toInt())
        }
    } else {
        InitialAvatar(name = name, size = size, modifier = modifier, onClick = onClick)
    }
}

/** Round black badge with a platform's short mark. */
@Composable
fun PlatformBadge(platform: Platform, size: Dp = 44.dp, filled: Boolean = true) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) Brand.Ink else platform.cardColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            platform.badge,
            color = if (filled) Color.White else Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.32f).sp,
        )
    }
}

/** Avatar for a tracked account displaying profile photo with platform badge overlay. */
@Composable
fun AccountAvatar(acc: com.trackme.app.data.TrackedAccount, size: Dp = 44.dp) {
    val imgUrl = acc.avatarUrl
    Box(Modifier.size(size)) {
        if (!imgUrl.isNullOrBlank()) {
            RemoteAvatar(url = imgUrl, size = size.value.toInt())
            Box(
                Modifier
                    .size(size * 0.42f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Brand.Ink),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    acc.platform.badge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.16f).sp
                )
            }
        } else {
            PlatformBadge(acc.platform, size)
        }
    }
}

// ---------- Bottom navigation: Home | scan | Profile ----------

@Composable
fun HomeNav(
    selected: Screen,
    onHome: () -> Unit,
    onProfile: () -> Unit,
    onScan: () -> Unit,
    onNews: () -> Unit,
    onFiles: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .height(68.dp)
            .clip(RoundedCornerShape(50))
            .background(Brand.Panel)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NavItem(Icons.Default.Home, "Home", selected == Screen.HOME, onHome)
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.White)
                .pointerInput(Unit) {
                    var totalDragY = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDragY = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragY += dragAmount
                            if (totalDragY < -40f) {
                                onNews()
                                totalDragY = 0f
                            } else if (totalDragY > 40f) {
                                onHome()
                                totalDragY = 0f
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onScan() },
                        onDoubleTap = { onFiles() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan a tag", tint = Color.Black)
        }
        NavItem(Icons.Default.Person, "Profile", selected == Screen.PROFILE, onProfile)
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) Brand.Coral else Color(0xFF8A8A8A)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (selected) Color.White else tint, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

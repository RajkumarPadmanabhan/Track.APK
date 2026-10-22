package com.trackme.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
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
        val c = with(density) { corner.toPx() }
        val w = size.width
        val h = size.height
        val nw = with(density) { notchWidth.toPx() }.coerceIn(c * 2, w - c * 2)
        val nh = with(density) { notchHeight.toPx() }.coerceIn(c, h - c * 2)
        val f = c * 0.7f
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(w - c, 0f)
            quadraticBezierTo(w, 0f, w, c)
            lineTo(w, h - c)
            quadraticBezierTo(w, h, w - c, h)
            lineTo(nw + f, h)
            quadraticBezierTo(nw, h, nw, h - f)
            lineTo(nw, h - nh + f)
            quadraticBezierTo(nw, h - nh, nw - f, h - nh)
            lineTo(c, h - nh)
            quadraticBezierTo(0f, h - nh, 0f, h - nh - c)
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
    Canvas(modifier) {
        val n = values.size
        if (n == 0) return@Canvas
        val g = gap.toPx()
        val bw = ((size.width - g * (n - 1)) / n).coerceAtLeast(1f)
        values.forEachIndexed { i, raw ->
            val v = raw.coerceIn(0f, 1f)
            val h = if (v <= 0f) size.height * 0.12f else size.height * (0.22f + 0.78f * v)
            drawRoundRect(
                color = if (v <= 0f) dim else color,
                topLeft = Offset(i * (bw + g), (size.height - h) / 2f),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f),
            )
        }
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

// ---------- Bottom navigation: Home | scan | Profile ----------

@Composable
fun HomeNav(selected: Screen, onHome: () -> Unit, onProfile: () -> Unit, onScan: () -> Unit) {
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
                .clickable { onScan() },
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

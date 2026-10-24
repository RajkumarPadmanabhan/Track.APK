package com.trackme.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.data.BadgeBook
import com.trackme.app.data.EarnedBadge

private val activityMarks = listOf(
    Icons.Default.Bolt to Color(0xFFFF7043),
    Icons.Default.LocalFireDepartment to Color(0xFFE53935),
    Icons.Default.EmojiEvents to Color(0xFFFFB300),
    Icons.Default.AutoAwesome to Color(0xFF7E57C2),
    Icons.Default.WorkspacePremium to Color(0xFF26C6DA),
    Icons.Default.Star to Color(0xFF66BB6A),
)

private fun badgeStyle(id: String): Pair<ImageVector, Color> = when (id) {
    BadgeBook.SCAN_1 -> Icons.Default.Star to Color(0xFFF9A825)
    BadgeBook.SCAN_5 -> Icons.Default.WorkspacePremium to Color(0xFF7C4DFF)
    BadgeBook.SIZE_100 -> Icons.Default.Cloud to Color(0xFF00897B)
    BadgeBook.DAYS_7 -> Icons.Default.CalendarMonth to Color(0xFF1E88E5)
    else -> {
        val step = id.removePrefix("act_").toIntOrNull() ?: 0
        activityMarks[Math.floorMod(step / 10, activityMarks.size)]
    }
}

@Composable
fun BadgeMark(id: String, size: Dp, modifier: Modifier = Modifier) {
    val (icon, color) = badgeStyle(id)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.72f))))
            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = BadgeBook.title(id), tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

@Composable
fun VerifyTick(verified: Boolean, onClick: (() -> Unit)?) {
    val bg = if (verified) Color(0xFF1D9BF0) else Color(0xFF8E8E8E)
    Box(
        Modifier
            .size(22.dp)
            .then(if (!verified && onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outer = size.minDimension / 2f
            val inner = outer * 0.84f
            val teeth = 16
            val seal = Path()
            val steps = teeth * 2
            for (i in 0 until steps) {
                val angle = Math.toRadians(-90.0 + i * (360.0 / steps))
                val radius = if (i % 2 == 0) outer else inner
                val x = cx + (radius * kotlin.math.cos(angle)).toFloat()
                val y = cy + (radius * kotlin.math.sin(angle)).toFloat()
                if (i == 0) seal.moveTo(x, y) else seal.lineTo(x, y)
            }
            seal.close()
            drawPath(seal, bg)
            val s = size.minDimension
            val stroke = s * 0.09f
            val start = Offset(cx - s * 0.22f, cy + s * 0.02f)
            val corner = Offset(cx - s * 0.06f, cy + s * 0.18f)
            val tip = Offset(cx + s * 0.24f, cy - s * 0.16f)
            drawLine(Color.White, start, corner, strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(Color.White, corner, tip, strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun AchievementsDialog(
    earned: List<EarnedBadge>,
    pinned: List<String>,
    activity: Long,
    onTogglePin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = BadgeBook.showcase(earned, activity).filter { (_, id) -> id !in pinned }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Achievements", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rows.forEach { (got, id) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BadgeMark(id, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(BadgeBook.title(id), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(BadgeBook.detail(id), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (got != null) BadgeBook.dateLabel(got.earnedAt) else "Not yet",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (got != null) {
                            TextButton(onClick = { onTogglePin(id) }) { Text("Add") }
                        }
                    }
                }
                if (rows.isEmpty()) {
                    Text("Added badges are on your profile.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

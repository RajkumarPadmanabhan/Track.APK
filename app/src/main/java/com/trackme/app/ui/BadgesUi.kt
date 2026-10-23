package com.trackme.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.data.BadgeBook
import com.trackme.app.data.EarnedBadge

@Composable
fun BadgeMark(id: String, size: Dp, modifier: Modifier = Modifier) {
    val color = when {
        id == BadgeBook.SCAN_1 -> Color(0xFFFFC107)
        id == BadgeBook.SCAN_5 -> Color(0xFF7C4DFF)
        id == BadgeBook.SIZE_100 -> Color(0xFF26A69A)
        id == BadgeBook.DAYS_7 -> Color(0xFF42A5F5)
        id.startsWith("act_") -> Color(0xFFFF7043)
        else -> Color(0xFFB0BEC5)
    }
    val mark = when (id) {
        BadgeBook.SCAN_1 -> "★"
        BadgeBook.SCAN_5 -> "5"
        BadgeBook.SIZE_100 -> "MB"
        BadgeBook.DAYS_7 -> "7"
        else -> id.removePrefix("act_")
    }
    Box(
        modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            mark,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * if (mark.length > 3) 0.28f else 0.38f).sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun VerifyTick(verified: Boolean, onClick: (() -> Unit)?) {
    val bg = if (verified) Color(0xFF3897F0) else Color(0xFF8E8E8E)
    Box(
        Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (!verified && onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            Icons.Default.Check,
            contentDescription = if (verified) "Verified" else "Enter a promo code",
            tint = Color.White,
            modifier = Modifier.size(11.dp),
        )
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
    val rows = BadgeBook.showcase(earned, activity)
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
                            TextButton(onClick = { onTogglePin(id) }) {
                                Text(if (id in pinned) "Added" else "Add")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

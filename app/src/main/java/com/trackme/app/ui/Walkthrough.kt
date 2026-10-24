package com.trackme.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class TourMarks {
    val bounds: SnapshotStateMap<String, Rect> = SnapshotStateMap()
}

val LocalTourMarks = compositionLocalOf { TourMarks() }

@Composable
fun Modifier.markTour(key: String): Modifier {
    val marks = LocalTourMarks.current
    return this.onGloballyPositioned { marks.bounds[key] = it.boundsInRoot() }
}

private data class TourStep(
    val key: String,
    val title: String,
    val body: String,
    val fallbackX: Float,
    val fallbackY: Float,
)

private val tourSteps = listOf(
    TourStep("avatar", "Profile picture", "Opens your profile. Backup, restore, and sign out are there.", 0.86f, 0.08f),
    TourStep("refresh", "Refresh", "Reads the latest counts from the pages you saved.", 0.9f, 0.2f),
    TourStep("activity", "Activity", "The bars and the graph follow your saved counts. They stay flat until a count changes.", 0.5f, 0.34f),
    TourStep("contributions", "Contributions this year", "Tap the number to start the monkey. The card color changes only then. Tap the card to jump.", 0.28f, 0.46f),
    TourStep("layout", "Your platforms", "Each card is a page you scanned. This button switches list and grid.", 0.86f, 0.62f),
    TourStep("scan", "Scan", "Tap to add a link or upload a file. Double tap for expenses. Swipe the page left for news, then files.", 0.5f, 0.9f),
    TourStep("home", "Home", "Brings you back to this page.", 0.18f, 0.9f),
    TourStep("navprofile", "Profile", "Opens settings, backup, and sign out.", 0.82f, 0.9f),
)

@Composable
fun FirstWalkthrough(onDone: () -> Unit) {
    val marks = LocalTourMarks.current
    var step by remember { mutableIntStateOf(0) }
    var root by remember { mutableStateOf(Offset.Zero) }
    val current = tourSteps[step]
    val last = step == tourSteps.lastIndex
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { root = it.positionInRoot() },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        )
        Canvas(Modifier.fillMaxSize()) {
            val measured = marks.bounds[current.key]
            val rect = if (measured != null) {
                Rect(
                    measured.left - root.x,
                    measured.top - root.y,
                    measured.right - root.x,
                    measured.bottom - root.y,
                )
            } else {
                val cx = size.width * current.fallbackX
                val cy = size.height * current.fallbackY
                Rect(cx - 28f, cy - 28f, cx + 28f, cy + 28f)
            }
            drawRoundRect(
                Color.White,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(28f, 28f),
                style = Stroke(width = 4f),
            )
            val target = rect.center
            val from = if (target.y < size.height * 0.55f) {
                Offset(size.width / 2f, size.height * 0.72f)
            } else {
                Offset(size.width / 2f, size.height * 0.28f)
            }
            drawLine(Color.White, from, target, strokeWidth = 6f)
            val dx = target.x - from.x
            val dy = target.y - from.y
            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val ux = dx / len
            val uy = dy / len
            val px = -uy
            val py = ux
            val tip = target
            val base = Offset(target.x - ux * 28f, target.y - uy * 28f)
            val path = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(base.x + px * 12f, base.y + py * 12f)
                lineTo(base.x - px * 12f, base.y - py * 12f)
                close()
            }
            drawPath(path, Color.White)
        }
        Column(
            Modifier
                .align(if ((marks.bounds[current.key]?.center?.y ?: (root.y + 400f)) - root.y < 700f) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A), RoundedCornerShape(24.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(current.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(current.body, color = Color(0xFFD0D0D0), fontSize = 15.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDone) { Text("Skip") }
                Button(onClick = { if (last) onDone() else step++ }) {
                    Text(if (last) "Done" else "Next")
                }
            }
        }
    }
}

package com.trackme.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.Screen
import com.trackme.app.TrackViewModel
import com.trackme.app.data.Days
import com.trackme.app.data.TrackedAccount
import com.trackme.app.data.combinedActivity
import com.trackme.app.data.compactCount
import com.trackme.app.data.computeStreak
import com.trackme.app.data.deltaToday
import com.trackme.app.data.eventsOn
import com.trackme.app.data.spark
import com.trackme.app.data.weekEvents
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

private fun todayLabel(): String =
    java.text.SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())

@Composable
fun HomeScreen(vm: TrackViewModel) {
    val accounts = vm.accounts
    val session = vm.session ?: return
    val combined = remember(accounts) { combinedActivity(accounts) }
    val streak = remember(combined) { computeStreak(combined) }
    val todayEvents = combined[Days.key()] ?: 0
    val gained = accounts.sumOf { (it.deltaToday() ?: 0L).coerceAtLeast(0L) }
    val hasGit = accounts.any { it.platform.fullScan }

    val newsFeed by vm.newsFeed.collectAsState()
    val isNewsLoading by vm.isNewsLoading.collectAsState()
    val userPlatforms = remember(accounts) { accounts.map { it.platform }.distinct() }

    // Pull today's numbers when the home page opens (skipped if they are fresh).
    LaunchedEffect(accounts.size) { vm.refreshAccounts() }

    LaunchedEffect(userPlatforms) {
        vm.loadPersonalizedNews(userPlatforms)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(session.name, session.avatarUrl, onProfile = { vm.screen = Screen.PROFILE }) }

        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Today's snapshot", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(todayLabel(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (vm.refreshing) {
                    CircularProgressIndicator(Modifier.size(22.dp).padding(end = 0.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                } else if (accounts.isNotEmpty()) {
                    IconButton(onClick = { vm.refreshAccounts(force = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }
        }

        vm.refreshNote?.let { note ->
            item {
                Text(note, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }

        item { HeroTile(todayEvents, gained, accounts.size, hasGit, combined) }

        if (todayEvents > 0) {
            // Git activity today: show the activity card (can be skipped) instead of streak tiles.
            if (vm.gitSkipDay != Days.key()) {
                item { HeatmapTile(combined, onSkip = { vm.skipGitToday() }) }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        Icons.Default.LocalFireDepartment, "Streak", streak.current.toString(),
                        "Longest: ${streak.longest}", Modifier.weight(1f),
                    )
                    StatTile(
                        Icons.Default.CheckCircle, "Active days", streak.activeDays.toString(),
                        streak.since?.let { "Since ${Days.pretty(it)}" } ?: "No activity yet", Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Your platforms", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 9.dp, vertical = 2.dp),
                ) { Text(accounts.size.toString(), color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f))
                Text(
                    "+ Scan tag",
                    color = Brand.Coral,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { vm.openAdd() }.padding(8.dp),
                )
            }
        }

        if (accounts.isEmpty()) {
            item { EmptyTags(onScan = { vm.openAdd() }) }
        } else {
            items(accounts.distinctBy { it.id }, key = { it.id }) { acc -> AccountCard(acc) { vm.openAccount(acc.id) } }
        }

        // --- Live News & Trends Section ---
        if (newsFeed.isNotEmpty() || isNewsLoading) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡ Trends For You",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (isNewsLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Brand.Coral
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(newsFeed, key = { "news-" + it.id }) { post ->
                NewsPostCard(post = post)
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ---------- Header ("Good morning, Alex") ----------

@Composable
private fun Header(name: String, avatarUrl: String?, onProfile: () -> Unit) {
    val first = name.trim().substringBefore(" ").ifBlank { "there" }
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${greeting()},", fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Medium)
            Text(
                first,
                fontSize = 38.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        UserAvatar(name, avatarUrl, 52.dp, onClick = onProfile)
    }
}

// ---------- Hero tile (light "Statistic" card) ----------

@Composable
private fun HeroTile(
    todayEvents: Int,
    gained: Long,
    tags: Int,
    hasGit: Boolean,
    combined: Map<String, Int>,
) {
    val ink = Brand.Ink
    val bars = remember(combined) {
        val raw = (35 downTo 0).map { (combined[Days.key(-it)] ?: 0).toFloat() }
        val max = raw.maxOrNull() ?: 0f
        if (max <= 0f) raw.map { 0f } else raw.map { it / max }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brand.Sage)
            .padding(20.dp),
    ) {
        Text("Activity", fontSize = 44.sp, fontWeight = FontWeight.Medium, color = ink, letterSpacing = (-1).sp)
        Spacer(Modifier.height(10.dp))
        WaveformBars(bars, Modifier.fillMaxWidth().height(56.dp), ink, ink.copy(alpha = 0.16f))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1.3f)) {
                Text(todayEvents.toString(), fontSize = 54.sp, fontWeight = FontWeight.Medium, color = ink, lineHeight = 56.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (hasGit) "Git events today" else "Add GitHub or GitLab",
                    fontSize = 12.sp, color = ink.copy(alpha = 0.6f),
                )
            }
            HeroSmall(if (gained > 0) "+${compactCount(gained)}" else "0", "New followers", Modifier.weight(1f))
            HeroSmall(tags.toString(), "Tags", Modifier.weight(0.7f))
        }
    }
}

@Composable
private fun HeroSmall(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontSize = 26.sp, color = Brand.Ink.copy(alpha = 0.45f), fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = Brand.Ink.copy(alpha = 0.55f), maxLines = 1)
    }
}

// ---------- Heatmap tile ----------

@Composable
private fun HeatmapTile(combined: Map<String, Int>, onSkip: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF45161F), Color(0xFF2A0E14))))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Git activity", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                Text("You're active today", fontSize = 12.sp, color = Color(0xFFE9C7CE))
            }
            Text(
                "Skip",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        YearHeatmap(combined)
        val total = (0..370).sumOf { combined[Days.key(-it)] ?: 0 }
        Text(
            "%,d contributions in the last year".format(total),
            fontSize = 13.sp,
            color = Color(0xFFE9C7CE),
        )
    }
}

// ---------- Stat tile ("Streak", "Active days") ----------

@Composable
private fun StatTile(icon: ImageVector, label: String, value: String, footer: String, modifier: Modifier) {
    Tile(modifier.height(150.dp)) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(16.dp), tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Text(value, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Brand.Coral, maxLines = 1)
            Text(footer, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------- Platform card (coloured card with a notch + dark "Today" tab) ----------

private enum class Tone { UP, DOWN, FLAT }

private data class CardModel(
    val big: String,
    val bigLabel: String,
    val spark: List<Float>,
    val panelLabel: String,
    val panelValue: String,
    val tone: Tone,
    val extras: List<Pair<String, String>>,
)

private fun cardModel(a: TrackedAccount): CardModel {
    if (a.platform.fullScan) {
        val today = a.eventsOn(0)
        val week = a.weekEvents()
        return CardModel(
            big = today.toString(),
            bigLabel = "Git events today",
            spark = a.spark(),
            panelLabel = "This week",
            panelValue = "$week event" + if (week == 1) "" else "s",
            tone = if (week > 0) Tone.UP else Tone.FLAT,
            extras = buildList {
                a.count?.let { add(a.countLabel to compactCount(it)) }
                a.extras.firstOrNull { it.first == "Repos" }?.let { add(it) }
            },
        )
    }
    val delta = a.deltaToday()
    val (value, tone) = when {
        a.count == null -> "Set count" to Tone.FLAT
        delta == null -> "First day" to Tone.FLAT
        delta > 0 -> "+${compactCount(delta)}" to Tone.UP
        delta < 0 -> "-${compactCount(-delta)}" to Tone.DOWN
        else -> "0" to Tone.FLAT
    }
    return CardModel(
        big = a.count?.let { compactCount(it) } ?: "—",
        bigLabel = a.countLabel,
        spark = a.spark(),
        panelLabel = "Today",
        panelValue = value,
        tone = tone,
        extras = a.extras.take(2),
    )
}

@Composable
private fun AccountCard(acc: TrackedAccount, onClick: () -> Unit) {
    val m = remember(acc) { cardModel(acc) }
    val ink = Brand.Ink
    val notchW = 188.dp
    val notchH = 70.dp
    val shape = remember { NotchShape(28.dp, notchW, notchH) }

    Box(Modifier.fillMaxWidth().height(216.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(acc.platform.cardColor)
                .clickable(onClick = onClick),
        ) {
            Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlatformBadge(acc.platform, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            acc.name.ifBlank { acc.handle },
                            color = ink, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${acc.platform.label} · @${acc.handle}",
                            color = ink.copy(alpha = 0.6f), fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.big, color = ink, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, lineHeight = 50.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(m.bigLabel, color = ink.copy(alpha = 0.65f), fontSize = 13.sp)
                    }
                    WaveformBars(m.spark, Modifier.width(92.dp).height(48.dp), ink, ink.copy(alpha = 0.18f))
                }
            }
            // Right of the notch: two small secondary numbers
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                m.extras.forEach { (label, value) ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(value, color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(label, color = ink.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        // The dark tab that sits inside the notch
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .width(notchW - 8.dp)
                .height(notchH - 8.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brand.Panel)
                .clickable(onClick = onClick)
                .padding(start = 18.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(m.panelLabel, color = Color(0xFF9A9A9A), fontSize = 12.sp)
                Text(
                    m.panelValue,
                    color = when (m.tone) {
                        Tone.UP -> Brand.UpOnDark
                        Tone.DOWN -> Color(0xFFFF8A8F)
                        Tone.FLAT -> Color.White
                    },
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Open", tint = Color.Black, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptyTags(onScan: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Brand.Coral.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.QrCodeScanner, null, tint = Brand.Coral) }
        Text("Scan your first tag", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Add GitHub, GitLab, YouTube, Instagram, LinkedIn or X. Their daily counts and git activity show up here.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Button(
            onClick = onScan,
            colors = ButtonDefaults.buttonColors(containerColor = Brand.Coral, contentColor = Color.Black),
        ) { Text("Scan a tag", fontWeight = FontWeight.Bold) }
    }
}

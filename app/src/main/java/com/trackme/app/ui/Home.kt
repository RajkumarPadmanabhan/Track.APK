package com.trackme.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import kotlin.random.Random
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.trackme.app.data.Platform
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.Screen
import com.trackme.app.TrackViewModel
import com.trackme.app.data.Days
import com.trackme.app.data.TrackedAccount
import com.trackme.app.data.siteKey
import com.trackme.app.data.siteLabel
import com.trackme.app.data.combinedActivity
import com.trackme.app.data.compactCount
import com.trackme.app.data.formatRupees
import com.trackme.app.data.parseCount
import com.trackme.app.data.tagLine
import com.trackme.app.data.computeStreak
import com.trackme.app.data.deltaToday
import com.trackme.app.data.eventsOn
import com.trackme.app.data.spark
import com.trackme.app.data.weekEvents
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class RainDrop(
    val x: Float,
    val y: Float,
    val length: Float,
    val speed: Float,
    val alpha: Float,
)

@Composable
private fun RainEffect(modifier: Modifier = Modifier, dropCount: Int = 40) {
    val infiniteTransition = rememberInfiniteTransition(label = "rain")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rainTime",
    )
    val drops = remember {
        List(dropCount) {
            RainDrop(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                length = 20f + Random.nextFloat() * 30f,
                speed = 0.55f + Random.nextFloat() * 0.9f,
                alpha = 0.28f + Random.nextFloat() * 0.35f,
            )
        }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drops.forEach { drop ->
            val startX = drop.x * w
            val startY = ((drop.y + time * drop.speed) % 1f) * h
            drawLine(
                color = Color(0xFF80D8FF).copy(alpha = drop.alpha),
                start = Offset(startX, startY),
                end = Offset(startX - drop.length * 0.2f, startY + drop.length),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun greetingWithSymbol(): Pair<String, String> {
    val cal = Calendar.getInstance()
    val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    return when {
        minutes in 5 * 60 until 12 * 60 -> "Good morning" to "☀️"
        minutes in 12 * 60 until 17 * 60 -> "Good afternoon" to "🌤️"
        minutes in 17 * 60 until 20 * 60 + 30 -> "Good evening" to "🌇"
        else -> "Good night" to "🌙"
    }
}

private fun todayLabel(): String =
    java.text.SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())

@Composable
fun HomeScreen(vm: TrackViewModel) {
    val accounts = vm.accounts
    val platformAccounts = accounts.filter { !it.asApp }
    val appAccounts = accounts.filter { it.asApp && !it.isHidden }
    val session = vm.session ?: return
    val combined = remember(accounts) { combinedActivity(accounts.filter { !it.isHidden }) }
    val streak = remember(combined) { computeStreak(combined) }
    val todayEvents = combined[Days.key()] ?: 0
    val gained = accounts.filter { !it.isHidden }.sumOf { (it.deltaToday() ?: 0L).coerceAtLeast(0L) }
    val hasGit = accounts.any { it.platform.fullScan }

    // Pull today's numbers when the home page opens (skipped if they are fresh).
    LaunchedEffect(accounts.size) { vm.refreshAccounts() }

    var isRaining by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }
    var showBadges by remember { mutableStateOf(false) }
    var appSearch by remember { mutableStateOf(false) }
    var appQuery by remember { mutableStateOf("") }
    var appToDelete by remember { mutableStateOf<TrackedAccount?>(null) }
    val context = LocalContext.current
    var selectedGroupForPopup by remember { mutableStateOf<List<TrackedAccount>?>(null) }

    LaunchedEffect(Unit) {
        isRaining = com.trackme.app.data.WeatherApi.checkIsRaining()
    }

    val yearContributions = remember(combined) { combined.values.sum() }
    val totalSubscribers = remember(accounts) { accounts.filter { !it.isHidden }.sumOf { it.count ?: 0L } }
    val totalGrowth = yearContributions.toLong() + totalSubscribers

    val siteGroups = platformAccounts.distinctBy { it.id }.siteGroups()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isRaining) {
            RainEffect(modifier = Modifier.fillMaxSize())
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Header(
                    name = session.name,
                    avatarUrl = session.avatarUrl,
                    onProfile = { vm.screen = Screen.PROFILE }
                )
            }

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
                        IconButton(onClick = { vm.refreshAccounts(force = true) }, modifier = Modifier.markTour("refresh")) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            }



            item {
                HeroTile(
                    totalGrowth = totalGrowth,
                    tags = platformAccounts.size,
                    combined = combined,
                    filesSize = com.trackme.app.data.formatBytes(vm.totalUploadedBytes),
                    badgeCount = vm.earnedBadges.size,
                    onBadges = { showBadges = true },
                )
            }

            if (appAccounts.isNotEmpty()) {
                val shownApps = appAccounts.filter { acc ->
                    val q = appQuery.trim()
                    if (!appSearch || q.isBlank()) true
                    else {
                        val label = acc.appName?.ifBlank { null } ?: acc.name.ifBlank { acc.handle }
                        label.contains(q, ignoreCase = true) || acc.handle.contains(q, ignoreCase = true)
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp).markTour("apps"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Apps", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            appSearch = !appSearch
                            if (!appSearch) appQuery = ""
                        }) {
                            Icon(
                                if (appSearch) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search apps",
                            )
                        }
                    }
                }
                if (appSearch) {
                    item {
                        OutlinedTextField(
                            value = appQuery,
                            onValueChange = { appQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            label = { Text("Search by name") },
                        )
                    }
                }
                item {
                    if (shownApps.isEmpty()) {
                        Text("No app with that name.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(shownApps, key = { it.id }) { acc ->
                                val label = acc.appName?.ifBlank { null } ?: acc.name.ifBlank { acc.handle.ifBlank { acc.platform.label } }
                                AppIcon(
                                    name = label,
                                    url = acc.url,
                                    platform = acc.platform,
                                    onClick = {
                                        if (acc.asApp) {
                                            val opened = runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(acc.url)))
                                            }.isSuccess
                                            if (!opened) Toast.makeText(context, "Couldn't open that link", Toast.LENGTH_SHORT).show()
                                        } else {
                                            vm.openAccount(acc.id)
                                        }
                                    },
                                    onLongClick = { if (acc.asApp) appToDelete = acc },
                                )
                            }
                        }
                    }
                }
            }

            if (hasGit || combined.isNotEmpty()) {
                val totalGitContributions = combined.values.sum()
                val isSkippedAndNoNewActivity = vm.gitSkippedContributions != -1 && totalGitContributions <= vm.gitSkippedContributions
                if (!isSkippedAndNoNewActivity) {
                    item { HeatmapTile(combined, onSkip = { vm.skipGit(totalGitContributions) }) }
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
                    ) { Text(platformAccounts.size.toString(), color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier
                            .markTour("layout")
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { isGridView = !isGridView }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.GridView else Icons.Default.ViewList,
                            contentDescription = "Toggle View Mode",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            if (isGridView) "Grid" else "List",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (platformAccounts.isEmpty() && appAccounts.isEmpty()) {
                item { EmptyTags(onScan = { vm.openAdd() }) }
            } else if (isGridView && siteGroups.isNotEmpty()) {
                items(
                    items = siteGroups.chunked(2),
                    key = { pair -> "grid-" + pair.joinToString("-") { it.firstOrNull()?.siteKey() ?: "x" } },
                ) { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { group ->
                            val key = group.first().siteKey()
                            val index = siteGroups.indexOfFirst { it.first().siteKey() == key }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SiteGroupBar(
                                    label = group.first().siteLabel(),
                                    count = group.size,
                                    canForward = index > 0,
                                    canBack = index in 0 until siteGroups.lastIndex,
                                    onForward = { vm.moveSite(key, forward = true) },
                                    onBack = { vm.moveSite(key, forward = false) },
                                )
                                if (group.size == 1) {
                                    GridAccountCard(group.first(), onToggleVisibility = { vm.toggleAccountVisibility(group.first().id) }) { vm.openAccount(group.first().id) }
                                } else {
                                    StackedGridAccountCard(group) { selectedGroupForPopup = group }
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else if (siteGroups.isNotEmpty()) {
                siteGroups.forEachIndexed { index, group ->
                    val key = group.first().siteKey()
                    item(key = "site-$key") {
                        SiteGroupBar(
                            label = group.first().siteLabel(),
                            count = group.size,
                            canForward = index > 0,
                            canBack = index < siteGroups.lastIndex,
                            onForward = { vm.moveSite(key, forward = true) },
                            onBack = { vm.moveSite(key, forward = false) },
                        )
                    }
                    items(group, key = { it.id }) { acc ->
                        AccountCard(
                            acc,
                            graphIndex = group.indexOfFirst { it.id == acc.id },
                            onToggleVisibility = { vm.toggleAccountVisibility(acc.id) },
                        ) { vm.openAccount(acc.id) }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        appToDelete?.let { acc ->
            AlertDialog(
                onDismissRequest = { appToDelete = null },
                title = { Text("Remove this shortcut?") },
                text = { Text("${acc.appName ?: acc.name} is only a link. Removing it does not delete anything on the website.") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.removeAccount(acc.id)
                        appToDelete = null
                    }) { Text("Remove") }
                },
                dismissButton = {
                    TextButton(onClick = { appToDelete = null }) { Text("Cancel") }
                },
            )
        }

        selectedGroupForPopup?.takeIf { it.size > 1 }?.let { group ->
            GroupOrderDialog(
                group = group,
                onDismiss = { selectedGroupForPopup = null },
                onOpen = { acc ->
                    selectedGroupForPopup = null
                    vm.openAccount(acc.id)
                },
                onReorder = { ids ->
                    runCatching { vm.reorderGroup(ids) }
                },
            )
        }
        if (showBadges) {
            AchievementsDialog(
                earned = vm.earnedBadges,
                pinned = vm.pinnedBadgeIds,
                activity = totalGrowth,
                onTogglePin = { vm.togglePinnedBadge(it) },
                onDismiss = { showBadges = false },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIcon(
    name: String,
    url: String,
    platform: com.trackme.app.data.Platform,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(76.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogo(appLogoKind(url, platform), 62.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GroupOrderDialog(
    group: List<TrackedAccount>,
    onDismiss: () -> Unit,
    onOpen: (TrackedAccount) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val orderKey = group.map { it.id }.joinToString()
    val holder = remember(orderKey) { object { var items = group } }
    var rows by remember(orderKey) { mutableStateOf(group) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }

    androidx.compose.ui.window.Dialog(onDismissRequest = {
        runCatching { onReorder(holder.items.map { it.id }) }
        onDismiss()
    }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brand.Panel)
                .padding(24.dp),
        ) {
            Text(
                "${group.firstOrNull()?.platform?.label ?: "Profiles"} profiles",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                "Drag a profile to the top. That one shows first on the card.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(12.dp))
            Column {
                for (acc in rows) {
                    key(acc.id) {
                    val dragging = draggingId == acc.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .zIndex(if (dragging) 1f else 0f)
                            .offset { IntOffset(0, if (dragging) dragDy.roundToInt() else 0) }
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (dragging) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Drag",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(40.dp)
                                .pointerInput(acc.id) {
                                    val itemHeight = 64.dp.toPx().coerceAtLeast(1f)
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingId = acc.id
                                            dragDy = 0f
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragDy = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragDy += amount.y
                                        },
                                        onDragEnd = {
                                            val current = holder.items
                                            val from = current.indexOfFirst { it.id == acc.id }
                                            val shift = (dragDy / itemHeight).toInt()
                                            if (from >= 0 && shift != 0 && current.isNotEmpty()) {
                                                val to = (from + shift).coerceIn(0, current.lastIndex)
                                                if (to != from) {
                                                    val next = current.toMutableList()
                                                    val moved = next.removeAt(from)
                                                    next.add(to, moved)
                                                    holder.items = next
                                                    rows = next
                                                    runCatching { onReorder(next.map { it.id }) }
                                                }
                                            }
                                            draggingId = null
                                            dragDy = 0f
                                        },
                                    )
                                },
                        )
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpen(acc) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AccountAvatar(acc, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    acc.name.ifBlank { acc.handle },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    acc.tagLine(withPlatform = false),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    }
                }
            }
            androidx.compose.material3.TextButton(
                onClick = {
                    runCatching { onReorder(holder.items.map { it.id }) }
                    onDismiss()
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Done", color = Brand.Coral)
            }
        }
    }
}

// ---------- Header ("Good morning ☀️, Alex") ----------

@Composable
private fun Header(
    name: String,
    avatarUrl: String?,
    onProfile: () -> Unit
) {
    val first = name.trim().substringBefore(" ").ifBlank { "there" }
    val (greetingText, symbol) = greetingWithSymbol()
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text("$greetingText $symbol,", fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium)
            Text(
                first,
                fontSize = 38.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        UserAvatar(name, avatarUrl, 52.dp, modifier = Modifier.markTour("avatar"), onClick = onProfile)
    }
}

// ---------- Hero tile (light "Statistic" card) ----------

@Composable
private fun HeroTile(
    totalGrowth: Long,
    tags: Int,
    combined: Map<String, Int>,
    filesSize: String = "0 B",
    badgeCount: Int = 0,
    onBadges: () -> Unit = {},
) {
    var bgIndex by remember { mutableIntStateOf(0) }
    var monkeyOn by remember { mutableStateOf(false) }
    var jumpToken by remember { mutableIntStateOf(0) }
    val bgColors = remember {
        listOf(
            Color(0xFFE8EDE0),
            Color(0xFFFFE4D8),
            Color(0xFFD7E8FF),
            Color(0xFFEBD8FF),
            Color(0xFFCFFAE4),
            Color(0xFFFFF6C2),
            Color(0xFFFFD5DE),
            Color(0xFFD2F3FF),
            Color(0xFFE7FFC4),
            Color(0xFFFFDCC4),
            Color(0xFFD9CBF2),
            Color(0xFFC4F6EE),
            Color(0xFFFFCDD2),
            Color(0xFFD4EDFF),
        )
    }
    val currentBg = bgColors[bgIndex % bgColors.size]
    val ink = Brand.Ink
    val bars = remember(combined) {
        val raw = (35 downTo 0).map { (combined[Days.key(-it)] ?: 0).toFloat() }
        val max = raw.maxOrNull() ?: 0f
        if (max <= 0f) raw.map { 0f } else raw.map { it / max }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(currentBg)
            .markTour("activity")
            .clickable {
                if (monkeyOn) jumpToken++ else bgIndex = (bgIndex + 1) % bgColors.size
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Activity", fontSize = 32.sp, fontWeight = FontWeight.Medium, color = ink, letterSpacing = (-0.5).sp, modifier = Modifier.weight(1f))
            if (badgeCount > 0) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ink.copy(alpha = 0.08f))
                        .clickable(onClick = onBadges)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BadgeMark(com.trackme.app.data.BadgeBook.SCAN_1, 18.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        badgeCount.toString(),
                        color = ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (monkeyOn) {
            MonkeyRun(
                jumpToken = jumpToken,
                ink = ink,
                onOut = { monkeyOn = false },
                modifier = Modifier.fillMaxWidth().height(72.dp),
            )
        } else {
            WaveformBars(bars, Modifier.fillMaxWidth().height(40.dp), ink, ink.copy(alpha = 0.16f))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(
                Modifier
                    .weight(1.35f)
                    .markTour("contributions")
                    .then(if (!monkeyOn) Modifier.clickable { monkeyOn = true } else Modifier),
            ) {
                val countText = compactCount(totalGrowth)
                Text(
                    countText,
                    fontSize = if (countText.length >= 7) 22.sp else 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = ink,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Contributions this year",
                    fontSize = 11.sp,
                    color = ink.copy(alpha = 0.6f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HeroSmall(tags.toString(), "Tags", Modifier.weight(0.7f))
            HeroSmall(
                value = filesSize,
                label = "Size",
                modifier = Modifier.weight(0.85f),
            )
        }
    }
}

private class MonkeyWorld {
    var y = 0f
    var vy = 0f
    var speed = 340f
    var passed = 0f
    val blocks = ArrayList<FloatArray>()
    var out = false

    fun reset() {
        y = 0f
        vy = 0f
        speed = 340f
        passed = 0f
        blocks.clear()
        blocks.add(floatArrayOf(640f, 36f))
        out = false
    }

    fun jump() {
        if (!out && y <= 1f) vy = 680f
    }

    fun step(dt: Float, width: Float, ceiling: Float) {
        if (out || width < 2f) return
        val frame = dt.coerceIn(0f, 0.032f)
        vy -= 2200f * frame
        y += vy * frame
        val maxY = (ceiling - 72f).coerceIn(56f, 150f)
        if (y > maxY) {
            y = maxY
            if (vy > 0f) vy = 0f
        }
        if (y <= 0f) {
            y = 0f
            if (vy < 0f) vy = 0f
        }
        speed = (speed + 18f * frame).coerceAtMost(620f)
        val stepX = speed * frame
        for (b in blocks) b[0] -= stepX
        blocks.removeAll { it[0] < -40f }
        val last = blocks.maxOfOrNull { it[0] } ?: 0f
        if (last < width * 0.55f) {
            val gap = 220f + (width * 0.15f * ((passed.toInt() % 3) + 1) * 0.15f)
            blocks.add(floatArrayOf(last + gap + 180f, if (blocks.size % 2 == 0) 34f else 52f))
        }
        val monkeyLeft = 30f
        val monkeyRight = 60f
        for (b in blocks) {
            val hitX = b[0] < monkeyRight && b[0] + 18f > monkeyLeft
            val hitY = y < b[1] - 6f
            if (hitX && hitY) {
                out = true
                return
            }
        }
        passed += dt
    }
}

@Composable
private fun MonkeyRun(jumpToken: Int, ink: Color, onOut: () -> Unit, modifier: Modifier) {
    val world = remember { MonkeyWorld() }
    var tick by remember { mutableIntStateOf(0) }
    var width by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { world.reset() }
    LaunchedEffect(jumpToken) { if (jumpToken > 0) world.jump() }
    LaunchedEffect(width, height) {
        if (width < 2f || height < 2f) return@LaunchedEffect
        var last = 0L
        while (!world.out) {
            withFrameNanos { t ->
                if (last != 0L && !world.out) world.step((t - last) / 1_000_000_000f, width, height - 8f)
                last = t
                tick++
            }
        }
        onOut()
    }
    Canvas(
        modifier.onSizeChanged {
            width = it.width.toFloat()
            height = it.height.toFloat()
        },
    ) {
        val ground = size.height - 8f
        drawLine(ink.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(0f, ground), androidx.compose.ui.geometry.Offset(size.width, ground), 3f)
        val seen = tick
        if (seen < 0) return@Canvas
        for (b in world.blocks) {
            drawRoundRect(
                ink,
                androidx.compose.ui.geometry.Offset(b[0], ground - b[1]),
                androidx.compose.ui.geometry.Size(16f, b[1]),
                androidx.compose.ui.geometry.CornerRadius(5f, 5f),
            )
        }
        val base = ground - world.y
        val mx = 28f
        drawCircle(Color(0xFF8D5524), 7f, androidx.compose.ui.geometry.Offset(mx + 8f, base - 46f))
        drawCircle(Color(0xFF8D5524), 7f, androidx.compose.ui.geometry.Offset(mx + 24f, base - 46f))
        drawCircle(Color(0xFFC68642), 16f, androidx.compose.ui.geometry.Offset(mx + 18f, base - 22f))
        drawCircle(Color(0xFFE8B86D), 13f, androidx.compose.ui.geometry.Offset(mx + 24f, base - 40f))
        drawCircle(ink, 2.1f, androidx.compose.ui.geometry.Offset(mx + 28f, base - 42f))
        drawCircle(Color(0xFF8D5524), 4f, androidx.compose.ui.geometry.Offset(mx + 4f, base - 8f))
        drawCircle(Color(0xFF8D5524), 4f, androidx.compose.ui.geometry.Offset(mx + 22f, base - 8f))
    }
}

@Composable
private fun HeroSmall(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(
            value,
            fontSize = if (value.length > 5) 14.sp else 18.sp,
            color = Brand.Ink,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = Brand.Ink.copy(alpha = 0.55f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
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
    if (a.isHidden) {
        return CardModel(
            big = "Hidden",
            bigLabel = if (a.count != null) a.countLabel else "Status",
            spark = emptyList(),
            panelLabel = "Today",
            panelValue = "-",
            tone = Tone.FLAT,
            extras = emptyList(),
        )
    }
    if (a.platform.fullScan) {
        val yearTotal = a.activity.values.sum()
        val week = a.weekEvents()
        return CardModel(
            big = compactCount(yearTotal.toLong()),
            bigLabel = "Contributions this year",
            spark = a.spark(),
            panelLabel = "This week",
            panelValue = "$week event" + if (week == 1) "" else "s",
            tone = if (week > 0) Tone.UP else Tone.FLAT,
            extras = a.extras.filter { it.first == "Repos" },
        )
    }
    val shownCount = a.count?.takeIf { it > 0L }
    val delta = a.deltaToday()
    val (value, tone) = when {
        shownCount == null -> "Open" to Tone.FLAT
        delta == null -> "First day" to Tone.FLAT
        delta > 0 -> "+${compactCount(delta)}" to Tone.UP
        delta < 0 -> "-${compactCount(-delta)}" to Tone.DOWN
        else -> "No change" to Tone.FLAT
    }
    val following = a.extras.firstOrNull { it.first.equals("Following", true) }?.second
        ?.let { raw -> parseCount(raw)?.takeIf { it > 0L }?.let { compactCount(it) } }
    val (big, bigLabel, extras) = when (a.platform) {
        Platform.AMAZON, Platform.FLIPKART -> {
            val cart = a.extras.count { it.first == "Cart" }
            val orders = a.extras.count { it.first == "Order" }
            val side = buildList {
                if (cart > 0) add("Cart" to cart.toString())
                if (orders > 0 && shownCount == null) add("Orders" to orders.toString())
            }
            when {
                shownCount != null -> Triple(formatRupees(shownCount), "Orders", side.filter { it.first == "Cart" })
                cart > 0 -> Triple(cart.toString(), "Cart", emptyList())
                else -> Triple("", "", emptyList())
            }
        }
        Platform.YOUTUBE -> Triple(
            shownCount?.let { compactCount(it) }.orEmpty(),
            if (shownCount != null) a.countLabel else "",
            a.extras.filter { it.first == "Videos" && it.second.isNotBlank() && it.second != "0" },
        )
        else -> when {
            shownCount != null && following != null -> Triple(
                compactCount(shownCount),
                a.countLabel.ifBlank { "Followers" },
                listOf("Following" to following),
            )
            shownCount != null -> Triple(compactCount(shownCount), a.countLabel.ifBlank { "Followers" }, emptyList())
            following != null -> Triple(following, "Following", emptyList())
            else -> Triple("", "", emptyList())
        }
    }
    return CardModel(
        big = big,
        bigLabel = bigLabel,
        spark = a.spark(),
        panelLabel = if (shownCount == null) "Profile" else "Today",
        panelValue = value,
        tone = tone,
        extras = extras,
    )
}

@Composable
private fun List<TrackedAccount>.siteGroups(): List<List<TrackedAccount>> {
    val map = LinkedHashMap<String, MutableList<TrackedAccount>>()
    for (acc in this) map.getOrPut(acc.siteKey()) { mutableListOf() }.add(acc)
    return map.values.toList()
}

@Composable
private fun SiteGroupBar(
    label: String,
    count: Int,
    canForward: Boolean,
    canBack: Boolean,
    onForward: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count > 1) {
            Text("$count", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = onForward, enabled = canForward, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move forward", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onBack, enabled = canBack, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move back", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AccountCard(acc: TrackedAccount, graphIndex: Int = 0, onToggleVisibility: () -> Unit, onClick: () -> Unit) {
    val m = remember(acc) { cardModel(acc) }
    val ink = Brand.Ink
    val notchW = 188.dp
    val notchH = 70.dp
    val shape = remember { NotchShape(28.dp, notchW, notchH) }

    Box(Modifier.fillMaxWidth().height(232.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(acc.siteColor())
                .clickable(onClick = onClick),
        ) {
            Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountAvatar(acc, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            acc.name.ifBlank { acc.handle },
                            color = ink, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            acc.tagLine(),
                            color = ink.copy(alpha = 0.6f), fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onToggleVisibility, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (acc.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Visibility",
                            tint = ink,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (m.big.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.big, color = ink, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, lineHeight = 42.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(m.bigLabel, color = ink.copy(alpha = 0.65f), fontSize = 13.sp)
                        }
                        CountChart(m.spark, Modifier.width(136.dp).height(74.dp), ink, graphKind(acc.id, graphIndex), acc.id.hashCode())
                    }
                }
            }
            // Right of the notch: secondary numbers (Videos, Repos, Following)
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
private fun GridAccountCard(acc: TrackedAccount, onToggleVisibility: () -> Unit, onClick: () -> Unit) {
    val m = remember(acc) { cardModel(acc) }

    val ink = Brand.Ink
    Column(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(acc.siteColor())
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountAvatar(acc, 36.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    acc.name.ifBlank { acc.handle },
                    color = ink, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    acc.tagLine(withPlatform = false),
                    color = ink.copy(alpha = 0.6f), fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleVisibility, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (acc.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle Visibility",
                    tint = ink,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (m.big.isNotBlank()) {
            Column {
                Text(m.big, color = ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Text(m.bigLabel, color = ink.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            acc.platform.label,
            color = ink.copy(alpha = 0.75f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StackedGridAccountCard(group: List<TrackedAccount>, onClick: () -> Unit) {
    val acc = group.first()
    val totalCount = group.filter { !it.isHidden }.mapNotNull { it.count }.sum()
    val label = if (group.any { it.platform.fullScan }) "Contributions" else acc.countLabel

    val ink = Brand.Ink
    Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(0.85f)
                .height(160.dp)
                .offset(x = (-8).dp, y = 8.dp)
                .rotate(-6f)
                .clip(RoundedCornerShape(28.dp))
                .background(acc.siteColor().copy(alpha = 0.45f))
        )
        Box(
            Modifier
                .fillMaxWidth(0.9f)
                .height(165.dp)
                .offset(x = 6.dp, y = 2.dp)
                .rotate(4f)
                .clip(RoundedCornerShape(28.dp))
                .background(acc.siteColor().copy(alpha = 0.7f))
        )
        Column(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(acc.siteColor())
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(ink),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+${group.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            acc.name.ifBlank { acc.handle },
                            color = ink, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Shows first · ${group.size}",
                            color = ink.copy(alpha = 0.6f), fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column {
                    val big = if (totalCount > 0) compactCount(totalCount) else "Multiple"
                    Text(big, color = ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    Text("Total $label", color = ink.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(acc.platform.label, color = ink.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.ViewList, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
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

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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import kotlin.random.Random
import com.trackme.app.data.Platform
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.Screen
import com.trackme.app.TrackViewModel
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.trackme.app.data.Days
import com.trackme.app.data.ScreenTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import com.trackme.app.data.TrackedAccount
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

private fun greetingWithSymbol(): Pair<String, String> {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h in 5..11 -> "Good morning" to "☀️"
        h in 12..16 -> "Good afternoon" to "🌤️"
        h in 17..21 -> "Good evening" to "🌇"
        else -> "Good night" to "🌙"
    }
}

private data class RainDrop(
    val x: Float,
    val y: Float,
    val length: Float,
    val speed: Float,
    val alpha: Float,
)

@Composable
fun RainEffect(
    modifier: Modifier = Modifier,
    dropCount: Int = 40
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rain")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainTime"
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

    androidx.compose.foundation.Canvas(modifier = modifier) {
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
                cap = StrokeCap.Round
            )
        }
    }
}

private fun todayLabel(): String =
    java.text.SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())

@Composable
fun HomeScreen(vm: TrackViewModel) {
    val accounts = vm.accounts
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
    var selectedGroupForPopup by remember { mutableStateOf<List<TrackedAccount>?>(null) }

    LaunchedEffect(Unit) {
        isRaining = com.trackme.app.data.WeatherApi.checkIsRaining()
    }

    val yearContributions = remember(combined) { combined.values.sum() }
    val totalSubscribers = remember(accounts) { accounts.filter { !it.isHidden }.sumOf { it.count ?: 0L } }
    val totalGrowth = yearContributions.toLong() + totalSubscribers

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
                        IconButton(onClick = { vm.refreshAccounts(force = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            }



            item { HeroTile(totalGrowth, accounts.size, combined) }

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
                    ) { Text(accounts.size.toString(), color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier
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

            if (accounts.isEmpty()) {
                item { EmptyTags(onScan = { vm.openAdd() }) }
            } else if (isGridView) {
                val gridGroups = accounts.distinctBy { it.id }.groupBy { it.platform }.values.toList()
                items(gridGroups.chunked(2)) { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { group ->
                            Box(Modifier.weight(1f)) {
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
            } else {
                items(accounts.distinctBy { it.id }, key = { it.id }) { acc -> AccountCard(acc, onToggleVisibility = { vm.toggleAccountVisibility(acc.id) }) { vm.openAccount(acc.id) } }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        if (selectedGroupForPopup != null) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { selectedGroupForPopup = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brand.Panel)
                ) {
                    Column(Modifier.padding(24.dp).fillMaxWidth()) {
                        Text("${selectedGroupForPopup!!.first().platform.label} Accounts", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(selectedGroupForPopup!!) { acc ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                        vm.openAccount(acc.id)
                                        selectedGroupForPopup = null
                                    }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AccountAvatar(acc, 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(acc.name.ifBlank { acc.handle }, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(acc.tagLine(withPlatform = false), color = Color.White.copy(alpha=0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.TextButton(onClick = { selectedGroupForPopup = null }, modifier = Modifier.align(Alignment.End)) {
                            Text("Cancel", color = Brand.Coral)
                        }
                    }
                }
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
        UserAvatar(name, avatarUrl, 52.dp, onClick = onProfile)
    }
}

// ---------- Hero tile (light "Statistic" card) ----------

@Composable
private fun HeroTile(
    totalGrowth: Long,
    tags: Int,
    combined: Map<String, Int>,
) {
    var bgIndex by remember { mutableStateOf(0) }
    val bgColors = remember {
        listOf(
            Brand.Sage,              // Default Sage Green
            Color(0xFFFFD1C1),       // Saturated Coral Sunset
            Color(0xFFC2D9FF),       // Vibrant Sky Blue
            Color(0xFFDEC2FF),       // Saturated Lavender
            Color(0xFFB5F2D2),       // Saturated Mint
            Color(0xFFFFEE9B),       // Saturated Golden Sun
            Color(0xFFFFC2CE),       // Vibrant Berry Pink
            Color(0xFFB8E8FC),       // Ocean Cyan
            Color(0xFFDAF7A6),       // Vibrant Lime
            Color(0xFFFFCBA4),       // Warm Apricot Tangerine
            Color(0xFFC3B1E1),       // Deep Periwinkle
            Color(0xFFA2E8DD),       // Vibrant Turquoise
            Color(0xFFFFB3BA),       // Saturated Flamingo Pink
            Color(0xFFBAE1FF),       // Deep Pastel Blue
        )
    }
    val currentBg = bgColors[bgIndex % bgColors.size]
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    var screenLabel by remember { mutableStateOf<String?>(null) }
    var usageAllowed by remember { mutableStateOf(false) }
    var usageChecked by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeKey) {
        while (isActive) {
            val (allowed, label) = withContext(Dispatchers.IO) {
                val access = ScreenTime.hasAccess(context)
                val text = if (access) {
                    ScreenTime.todayMillis(context)?.let { ScreenTime.format(it) }?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
                access to text
            }
            usageAllowed = allowed
            screenLabel = label
            usageChecked = true
            delay(30_000)
        }
    }
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
            .background(currentBg)
            .clickable { bgIndex = (bgIndex + 1) % bgColors.size }
            .padding(20.dp),
    ) {
        Text("Activity", fontSize = 44.sp, fontWeight = FontWeight.Medium, color = ink, letterSpacing = (-1).sp)
        Spacer(Modifier.height(10.dp))
        WaveformBars(bars, Modifier.fillMaxWidth().height(56.dp), ink, ink.copy(alpha = 0.16f))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1.2f)) {
                Text(compactCount(totalGrowth), fontSize = 52.sp, fontWeight = FontWeight.Medium, color = ink, lineHeight = 54.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Contributions this year",
                    fontSize = 12.sp, color = ink.copy(alpha = 0.6f),
                )
            }
            HeroSmall(tags.toString(), "Tags", Modifier.weight(0.4f))
            HeroSmall(
                value = screenLabel ?: if (!usageChecked || usageAllowed) "" else "Allow",
                label = "Time",
                modifier = Modifier.weight(0.5f).then(
                    if (usageAllowed) Modifier else Modifier.clickable {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                ),
            )
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
private fun AccountCard(acc: TrackedAccount, onToggleVisibility: () -> Unit, onClick: () -> Unit) {
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
                .background(acc.platform.cardColor)
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
                        WaveformBars(m.spark, Modifier.width(92.dp).height(44.dp), ink, ink.copy(alpha = 0.18f))
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

    Tile(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountAvatar(acc, 36.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        acc.name.ifBlank { acc.handle },
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        acc.tagLine(withPlatform = false),
                        color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onToggleVisibility, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (acc.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle Visibility",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (m.big.isNotBlank()) {
                Column {
                    Text(m.big, color = Brand.Coral, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(m.bigLabel, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            m.extras.firstOrNull()?.let { (label, value) ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StackedGridAccountCard(group: List<TrackedAccount>, onClick: () -> Unit) {
    val acc = group.first()
    val totalCount = group.filter { !it.isHidden }.mapNotNull { it.count }.sum()
    val label = if (group.any { it.platform.fullScan }) "Contributions" else acc.countLabel

    Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
        // Bottom layer (Left tilt)
        Tile(
            Modifier
                .fillMaxWidth(0.85f)
                .height(160.dp)
                .offset(x = (-8).dp, y = 8.dp)
                .rotate(-6f)
        ) { 
            Box(Modifier.fillMaxSize().background(acc.platform.cardColor.copy(alpha = 0.2f))) 
        }
        
        // Middle layer (Right tilt)
        Tile(
            Modifier
                .fillMaxWidth(0.9f)
                .height(165.dp)
                .offset(x = 6.dp, y = 2.dp)
                .rotate(4f)
        ) { 
            Box(Modifier.fillMaxSize().background(acc.platform.cardColor.copy(alpha = 0.35f))) 
        }

        // Top layer (Straight)
        Tile(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clickable(onClick = onClick)
        ) {
            Column(
                Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(acc.platform.cardColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+${group.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${acc.platform.label}s",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${group.size} Profiles",
                            color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column {
                    val big = if (totalCount > 0) compactCount(totalCount) else "Multiple"
                    Text(big, color = Brand.Coral, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text("Total $label", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tap to view", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Icon(Icons.Default.ViewList, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
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

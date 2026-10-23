@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.trackme.app.ui

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.trackme.app.data.YouTubeVideoItem
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.Screen
import com.trackme.app.TrackViewModel
import com.trackme.app.data.GhRepo
import com.trackme.app.data.GhUser
import com.trackme.app.data.ParsedProfile
import com.trackme.app.data.PublicProfile
import com.trackme.app.data.Platform
import com.trackme.app.data.SortMode
import com.trackme.app.data.TrackedAccount
import com.trackme.app.data.computeStreak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

// ---------- Helpers ----------

private fun fmt(n: Int) = "%,d".format(n)

private fun date(iso: String) = if (iso.length >= 10) iso.take(10) else iso

fun downloadRepo(context: Context, repo: GhRepo) {
    try {
        val req = DownloadManager.Request(Uri.parse(repo.zipUrl))
            .setTitle("${repo.name}.zip")
            .setDescription("Downloading ${repo.fullName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, "${repo.name}-${repo.defaultBranch}.zip"
            )
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        Toast.makeText(context, "Download started: ${repo.name}", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun shareText(context: Context, subject: String, text: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

fun shareRepo(context: Context, repo: GhRepo) {
    val text = buildString {
        append(repo.fullName)
        repo.description?.let { append(" - ").append(it) }
        append("\n").append(repo.htmlUrl)
    }
    shareText(context, repo.name, text, "Share repository")
}

private fun openUrl(context: Context, rawUrl: String) {
    try {
        val cleanUrl = if (!rawUrl.startsWith("http://", ignoreCase = true) && !rawUrl.startsWith("https://", ignoreCase = true)) {
            "https://$rawUrl"
        } else rawUrl
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this link", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
    }
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("TrackMe", text))
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
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
        Image(imageBitmap, contentDescription = "avatar", modifier = mod, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    } else {
        Box(mod.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

// ---------- Root ----------

@Composable
fun TrackMeApp(vm: TrackViewModel, sharedText: String?) {
    if (vm.session == null) LoginScreen(vm) else MainShell(vm, sharedText)
}

@Composable
private fun MainShell(vm: TrackViewModel, sharedText: String?) {
    var tab by rememberSaveable { mutableStateOf(0) }

    // A profile link shared into the app opens the scan sheet with that link.
    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank() && vm.input.isBlank()) {
            vm.openAdd()
            vm.scanText(sharedText)
        }
    }
    LaunchedEffect(vm.detailId) { tab = 0 }

    BackHandler(enabled = vm.screen != Screen.HOME) {
        if (vm.screen == Screen.DETAIL) vm.closeDetail() else vm.screen = Screen.HOME
    }

    val showDetailNav = vm.screen == Screen.DETAIL && vm.user != null &&
        vm.platform.fullScan && !vm.loading && vm.error == null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (vm.screen == Screen.DETAIL) {
                if (showDetailNav) PillNav(tab) { tab = it }
            } else {
                HomeNav(
                    selected = vm.screen,
                    onHome = { vm.screen = Screen.HOME },
                    onProfile = { vm.screen = Screen.PROFILE },
                    onScan = { vm.openAdd() },
                )
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.screen) {
                Screen.HOME -> HomeScreen(vm)
                Screen.PROFILE -> ProfileScreen(vm)
                Screen.DETAIL -> DetailScreen(vm, tab)
                Screen.LINKED_DEVICES -> LinkedDevicesScreen(
                    currentPlatforms = remember(vm.accounts) { com.trackme.app.data.StaticSyncManager.exportAccounts(vm.accounts) }
                )
            }
        }
    }

    if (vm.showAdd) AddTagSheet(vm)
}

// ---------- Tag detail (opened from a home card) ----------

@Composable
private fun DetailScreen(vm: TrackViewModel, tab: Int) {
    val acc = vm.accounts.firstOrNull { it.id == vm.detailId }
    val profile = vm.linkProfile
    val user = vm.user

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.closeDetail() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    acc?.name?.ifBlank { null } ?: vm.platform.label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${vm.platform.label}" + (acc?.let { " · @${it.handle}" } ?: ""),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            vm.error != null -> Text(
                vm.error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
            profile != null -> LinkProfileCard(vm.platform, profile, vm.preview, vm.previewNote) {
                if (acc != null && !acc.platform.fullScan) {
                    ManualCountTile(vm, acc)
                }
            }
            user != null -> when (tab) {
                0 -> OverviewTab(vm, user)
                1 -> GraphsTab(vm)
                else -> ReposTab(vm)
            }
        }
    }
}

/** For platforms that don't share follower counts: type today's number and TrackMe tracks the daily change. */
@Composable
private fun ManualCountTile(vm: TrackViewModel, acc: TrackedAccount) {
    var text by remember(acc.id) { mutableStateOf(acc.count?.toString() ?: "") }
    Tile(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Today's ${acc.countLabel.lowercase()}", fontWeight = FontWeight.Bold)
            Text(
                "${acc.platform.label} doesn't share this number, so type it in. " +
                    "TrackMe saves it each day and shows the change on your home page.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(12) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("e.g. 1200") },
                )
                Button(
                    onClick = { text.toLongOrNull()?.let { vm.setManualCount(acc.id, it) } },
                    enabled = text.toLongOrNull() != null,
                ) { Text("Save") }
            }
        }
    }
}


// ---------- Bottom pill navigation ----------

@Composable
private fun PillNav(selected: Int, onSelect: (Int) -> Unit) {
    val items: List<Pair<ImageVector, String>> = listOf(
        Icons.Default.Dashboard to "Overview",
        Icons.Default.BarChart to "Graphs",
        Icons.Default.Folder to "Repos",
    )
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { i, (icon, label) ->
                val sel = selected == i
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = if (sel) 16.dp else 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (sel) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    if (sel) {
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ---------- Link-only profile card (Instagram, LinkedIn, X, YouTube) ----------

@Composable
private fun LinkProfileCard(
    platform: Platform,
    profile: ParsedProfile,
    preview: PublicProfile?,
    note: String?,
    extra: @Composable () -> Unit = {},
) {
    val ctx = LocalContext.current
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (preview != null) PreviewCard(preview)
        extra()
        note?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Tile(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(platform.label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "@" + profile.handle.trimStart('@'),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(profile.url, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick = { openUrl(ctx, profile.url) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open profile")
        }
        OutlinedButton(
            onClick = { shareText(ctx, "${platform.label} profile", profile.url, "Share profile") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Share, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Share link")
        }
        OutlinedButton(onClick = { copyText(ctx, profile.url) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Copy link")
        }
    }
}

@Composable
private fun PreviewCard(p: PublicProfile) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val img = p.imageUrl
                if (!img.isNullOrBlank()) RemoteAvatar(img, 72)
                Column(Modifier.weight(1f)) {
                    Text(
                        p.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    p.subtitle?.let {
                        Text(
                            it,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        p.description?.let { Text(it, fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis) }
        p.stats.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) -> BigTile(label, value, null, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (p.recentVideos.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "🎬 Recent Videos & Shorts",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(p.recentVideos) { video ->
                    VideoPreviewCard(video) {
                        val videoUrl = if (video.title.contains("#shorts", ignoreCase = true)) {
                            "https://www.youtube.com/shorts/${video.id}"
                        } else {
                            "https://www.youtube.com/watch?v=${video.id}"
                        }
                        openUrl(ctx, videoUrl)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPreviewCard(video: YouTubeVideoItem, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val isShort = video.title.contains("#shorts", ignoreCase = true)
    val cardWidth = if (isShort) 130.dp else 200.dp
    val aspect = if (isShort) 9f / 16f else 16f / 9f

    Tile(
        Modifier
            .width(cardWidth)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (video.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = "Play",
                        tint = Brand.Coral,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (isShort) "⚡ Short" else "▶ Video",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                video.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (video.publishedAt.isNotBlank()) {
                Text(
                    date(video.publishedAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------- Overview ----------

@Composable
private fun BigTile(label: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    Tile(modifier.height(150.dp)) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 14.sp)
            Text(
                value,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                sub ?: "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OverviewTab(vm: TrackViewModel, user: GhUser) {
    val repos = vm.repos
    val ctx = LocalContext.current
    val totalStars = repos.sumOf { it.stars }
    val totalForks = repos.sumOf { it.forks }
    val originals = repos.count { !it.isFork }
    val langs = repos.mapNotNull { it.language }.groupingBy { it }.eachCount()
    val topLang = langs.maxByOrNull { it.value }?.key
    val top = repos.maxByOrNull { it.stars }
    val latest = repos.maxByOrNull { it.pushedAt }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RemoteAvatar(user.avatarUrl)
            Column {
                Text(user.name ?: user.login, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("@${user.login}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                user.location?.let { Text(it, fontSize = 13.sp) }
            }
        }
        user.bio?.let { Text(it) }
        if (user.createdAt.isNotBlank()) {
            Text(
                "Joined ${vm.platform.label} ${date(user.createdAt)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigTile("Repos", fmt(repos.size), "Originals: ${fmt(originals)}", Modifier.weight(1f))
            BigTile("Stars", fmt(totalStars), top?.takeIf { it.stars > 0 }?.let { "Top: ${it.name}" }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigTile("Forks", fmt(totalForks), null, Modifier.weight(1f))
            BigTile("Followers", fmt(user.followers), "Following: ${fmt(user.following)}", Modifier.weight(1f))
        }

        Tile(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Highlights", fontWeight = FontWeight.Bold)
                topLang?.let { Text("Top language: $it") }
                top?.takeIf { it.stars > 0 }?.let { Text("Most starred: ${it.name} (${fmt(it.stars)} stars)") }
                latest?.let { Text("Last active repo: ${it.name} on ${date(it.pushedAt)}") }
            }
        }

        OutlinedButton(
            onClick = { shareText(ctx, "${vm.platform.label} profile", user.htmlUrl, "Share profile") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Share, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Share profile")
        }
    }
}

// ---------- Graphs ----------

@Composable
private fun GraphsTab(vm: TrackViewModel) {
    val repos = vm.repos
    val langCounts = repos.mapNotNull { it.language }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    val langData = buildList {
        addAll(langCounts.take(8).map { it.key to it.value })
        val rest = langCounts.drop(8).sumOf { it.value }
        if (rest > 0) add("Other" to rest)
    }
    val topStars = repos.sortedByDescending { it.stars }.filter { it.stars > 0 }.take(8).map { it.name to it.stars }
    val topForks = repos.sortedByDescending { it.forks }.filter { it.forks > 0 }.take(8).map { it.name to it.forks }
    val byYear = repos.groupingBy { it.pushedAt.take(4) }.eachCount().toSortedMap()
        .filterKeys { it.isNotBlank() }.map { it.key to it.value }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChartCard("Git activity") {
            if (vm.activity.isEmpty()) {
                Text(
                    "No activity found for this profile.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val st = computeStreak(vm.activity)
                YearHeatmap(vm.activity)
                Text(
                    "Streak ${st.current} · Longest ${st.longest} · Active days ${st.activeDays}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ChartCard("Languages") {
            if (langData.isEmpty()) {
                Text(
                    "No language data available for this profile.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else LanguageDonut(langData)
        }
        ChartCard("Most starred repos") {
            if (topStars.isEmpty()) Text("No starred repos yet.") else HBarChart(topStars, chartColors[3])
        }
        ChartCard("Most forked repos") {
            if (topForks.isEmpty()) Text("No forked repos yet.") else HBarChart(topForks, chartColors[2])
        }
        ChartCard("Repos by last activity year") {
            HBarChart(byYear, chartColors[4])
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Tile(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

// ---------- Repo list ----------

@Composable
private fun ReposTab(vm: TrackViewModel) {
    val ctx = LocalContext.current
    val list = vm.visibleRepos()
    val languages = vm.repos.mapNotNull { it.language }.distinct().sorted()
    var sortMenu by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search repositories") },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(onClick = { sortMenu = true }) { Text("Sort: ${vm.sort.label}") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortMode.values().forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.label) },
                                    onClick = { vm.sort = m; sortMenu = false },
                                )
                            }
                        }
                    }
                    Text(
                        "${list.size} of ${vm.repos.size}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = vm.hideForks, onClick = { vm.hideForks = !vm.hideForks }, label = { Text("Hide forks") })
                    FilterChip(selected = vm.hideArchived, onClick = { vm.hideArchived = !vm.hideArchived }, label = { Text("Hide archived") })
                    languages.forEach { lang ->
                        FilterChip(
                            selected = vm.language == lang,
                            onClick = { vm.language = if (vm.language == lang) null else lang },
                            label = { Text(lang) },
                        )
                    }
                }
            }
        }
        items(list.distinctBy { it.fullName }, key = { it.fullName }) { repo -> RepoCard(repo, vm, ctx) }
    }
}

@Composable
private fun RepoCard(repo: GhRepo, vm: TrackViewModel, ctx: Context) {
    var showReleasesDialog by remember { mutableStateOf(false) }
    var showCollaboratorsDialog by remember { mutableStateOf(false) }
    var showActionsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(repo.fullName) {
        vm.loadReleasesForRepo(repo.fullName)
        vm.loadActionsForRepo(repo.fullName)
        vm.loadPagesForRepo(repo.fullName)
    }

    val releases = vm.releasesMap[repo.fullName]
    val isReleasesLoading = vm.loadingReleasesForRepo == repo.fullName
    val collaborators = vm.collaboratorsMap[repo.fullName]
    val isCollaboratorsLoading = vm.loadingCollaboratorsForRepo == repo.fullName
    val actions = vm.actionsMap[repo.fullName]
    val isActionsLoading = vm.loadingActionsForRepo == repo.fullName
    val pagesUrl = vm.pagesMap[repo.fullName]

    Tile(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    repo.name,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (repo.isFork) Text("fork", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (repo.archived) Text("  archived", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            }
            repo.description?.let {
                Text(it, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                repo.language?.let { Text(it, fontSize = 12.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, Modifier.size(14.dp))
                    Text(" ${repo.stars}", fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CallSplit, null, Modifier.size(14.dp))
                    Text(" ${repo.forks}", fontSize = 12.sp)
                }
                Text(date(repo.pushedAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { downloadRepo(ctx, repo) }) {
                    Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download")
                }
                OutlinedButton(onClick = { shareRepo(ctx, repo) }) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
                if (releases == null || releases.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        vm.loadReleasesForRepo(repo.fullName)
                        showReleasesDialog = true
                    }) {
                        Icon(Icons.Default.LocalOffer, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View releases")
                    }
                }
                OutlinedButton(onClick = {
                    vm.loadCollaboratorsForRepo(repo.fullName)
                    showCollaboratorsDialog = true
                }) {
                    Icon(Icons.Default.Group, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Collaborator")
                }
                OutlinedButton(onClick = {
                    vm.loadActionsForRepo(repo.fullName)
                    showActionsDialog = true
                }) {
                    Icon(Icons.Default.PlayCircle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Actions")
                }
                if (!pagesUrl.isNullOrBlank()) {
                    OutlinedButton(onClick = { openUrl(ctx, pagesUrl) }) {
                        Icon(Icons.Default.Language, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View page")
                    }
                }
            }
        }
    }

    if (showReleasesDialog) {
        ReleasesDialog(
            repo = repo,
            releases = releases,
            isLoading = isReleasesLoading,
            onDismiss = { showReleasesDialog = false }
        )
    }

    if (showCollaboratorsDialog) {
        CollaboratorsDialog(
            repo = repo,
            collaborators = collaborators,
            isLoading = isCollaboratorsLoading,
            onDismiss = { showCollaboratorsDialog = false }
        )
    }

    if (showActionsDialog) {
        ActionsDialog(
            repo = repo,
            runs = actions,
            isLoading = isActionsLoading,
            onDismiss = { showActionsDialog = false }
        )
    }
}

@Composable
private fun ReleasesDialog(
    repo: GhRepo,
    releases: List<com.trackme.app.data.GhRelease>?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${repo.name} Releases", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Repository versions & releases", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isLoading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (releases.isNullOrEmpty()) {
                    Text(
                        "No releases found for this repository.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    releases.forEach { rel ->
                        Tile(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        rel.name.ifBlank { rel.tagName },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Brand.Coral.copy(alpha = 0.2f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            rel.tagName,
                                            color = Brand.Coral,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                if (rel.publishedAt.isNotBlank()) {
                                    Text(
                                        "Released ${date(rel.publishedAt)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                rel.body?.takeIf { it.isNotBlank() }?.let { body ->
                                    Text(
                                        body,
                                        fontSize = 12.sp,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (rel.htmlUrl.isNotBlank()) {
                                    TextButton(onClick = { openUrl(ctx, rel.htmlUrl) }) {
                                        Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("View release on web", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun CollaboratorsDialog(
    repo: GhRepo,
    collaborators: List<com.trackme.app.data.GhCollaborator>?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${repo.name} Collaborators", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Contributors & team members", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isLoading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (collaborators.isNullOrEmpty()) {
                    Text(
                        "No collaborators or public contributors found.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    collaborators.forEach { collab ->
                        Tile(
                            Modifier
                                .fillMaxWidth()
                                .clickable { openUrl(ctx, collab.htmlUrl) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RemoteAvatar(collab.avatarUrl.orEmpty(), size = 42)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "@" + collab.login,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (collab.contributions > 0) {
                                        Text(
                                            "${collab.contributions} contribution${if (collab.contributions == 1) "" else "s"}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = "Open profile",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

fun exportActionRunToPdf(context: Context, repo: GhRepo, run: com.trackme.app.data.GhActionRun) {
    try {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint().apply {
            color = AndroidColor.DKGRAY
            textSize = 11f
        }
        val headerPaint = Paint().apply {
            color = AndroidColor.parseColor("#1F2937")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 11f
        }
        val linePaint = Paint().apply {
            color = AndroidColor.LTGRAY
            strokeWidth = 1f
        }

        var y = 40f
        canvas.drawText("TrackMe - GitHub Action Execution Report", 40f, y, titlePaint)
        y += 22f
        canvas.drawText("Repository: ${repo.fullName}", 40f, y, subPaint)
        y += 16f
        canvas.drawText("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}", 40f, y, subPaint)
        y += 20f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 28f

        canvas.drawText("Workflow Run Summary", 40f, y, headerPaint)
        y += 22f

        val conclusionStr = run.conclusion?.replaceFirstChar { it.uppercase() } ?: "In Progress / Queued"
        val details = listOf(
            "Run ID" to run.id.toString(),
            "Workflow Name" to run.name,
            "Run Title" to run.displayTitle,
            "Event Trigger" to run.event,
            "Status" to run.status.replaceFirstChar { it.uppercase() },
            "Conclusion" to conclusionStr,
            "Triggered By" to (run.authorName ?: "Automated Trigger"),
            "Created At" to run.createdAt,
            "Updated At" to run.updatedAt,
            "Run Web URL" to run.htmlUrl
        )

        for ((key, value) in details) {
            canvas.drawText("$key:", 40f, y, headerPaint)
            val valText = if (value.length > 55) value.take(52) + "..." else value
            canvas.drawText(valText, 170f, y, bodyPaint)
            y += 22f
            if (y > 780f) break
        }

        y += 10f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 28f

        val isSuccess = run.conclusion.equals("success", ignoreCase = true)
        val statusText = if (isSuccess) {
            "EXECUTION RESULT: SUCCESS - All jobs and steps passed without errors."
        } else if (run.conclusion != null) {
            "EXECUTION RESULT: ${run.conclusion?.uppercase()} - Run completed with status ${run.conclusion}."
        } else {
            "EXECUTION RESULT: RUNNING - Workflow run is in progress."
        }

        val statusPaint = Paint().apply {
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = if (isSuccess) AndroidColor.parseColor("#15803D") else AndroidColor.parseColor("#B91C1C")
        }
        canvas.drawText(statusText, 40f, y, statusPaint)

        document.finishPage(page)

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val fileName = "Action_Run_${repo.name}_${run.id}.pdf"
        val file = File(downloadsDir, fileName)
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        Toast.makeText(context, "PDF saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to save PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ActionsDialog(
    repo: GhRepo,
    runs: List<com.trackme.app.data.GhActionRun>?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${repo.name} Actions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("GitHub Workflows & Pipeline Runs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isLoading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (runs.isNullOrEmpty()) {
                    Text(
                        "No actions or workflow runs found for this repository.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    runs.forEach { run ->
                        val isSuccess = run.conclusion.equals("success", ignoreCase = true)
                        val isFailure = run.conclusion.equals("failure", ignoreCase = true)
                        val badgeColor = when {
                            isSuccess -> Color(0xFF2E7D32)
                            isFailure -> Color(0xFFC62828)
                            else -> Color(0xFF1976D2)
                        }

                        Tile(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        run.displayTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(badgeColor.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            run.conclusion?.replaceFirstChar { it.uppercase() } ?: run.status,
                                            color = badgeColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Text(
                                    "Workflow: ${run.name} · Event: ${run.event}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (run.createdAt.isNotBlank()) {
                                    Text(
                                        "Triggered ${date(run.createdAt)}" + (run.authorName?.let { " by @$it" } ?: ""),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Button(
                                        onClick = { exportActionRunToPdf(ctx, repo, run) },
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(Icons.Default.Download, null, Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("PDF Report", fontSize = 12.sp)
                                    }
                                    if (run.htmlUrl.isNotBlank()) {
                                        TextButton(onClick = { openUrl(ctx, run.htmlUrl) }) {
                                            Icon(Icons.Default.OpenInNew, null, Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Web", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

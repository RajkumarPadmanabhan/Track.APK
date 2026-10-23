@file:OptIn(ExperimentalLayoutApi::class)

package com.trackme.app.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import com.trackme.app.data.StaticSyncManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.Screen
import com.trackme.app.TrackViewModel
import com.trackme.app.data.TrackedAccount
import com.trackme.app.data.tagLine
import com.trackme.app.data.combinedActivity
import com.trackme.app.data.compactCount
import com.trackme.app.data.formatBytes
import java.io.File
import java.io.FileOutputStream

fun saveAvatarFromUri(context: Context, uri: Uri, email: String): String? {
    return try {
        val destFile = File(context.filesDir, "avatar_${email.replace(Regex("[^a-zA-Z0-9]"), "_")}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        destFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun ProfileScreen(vm: TrackViewModel) {
    val session = vm.session
    if (session == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }
    val activityTotal = remember(vm.accounts) {
        val visible = vm.accounts.filter { !it.isHidden }
        combinedActivity(visible).values.sum().toLong() + visible.sumOf { it.count ?: 0L }
    }
    var showOther by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var editProfile by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<TrackedAccount?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf(false) }
    var promoCode by remember { mutableStateOf("") }
    var promoError by remember { mutableStateOf(false) }
    var showBadges by remember { mutableStateOf(false) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportBackup(context, uri)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(context, uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ----- Profile card (dark, big name, white action button) -----
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF2A2A2A), Color(0xFF0E0E0E))))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                session.name,
                                fontSize = 36.sp,
                                lineHeight = 40.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(6.dp))
                            VerifyTick(vm.verified, onClick = { showCode = true })
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            session.email,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "v${vm.currentAppVersion}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    UserAvatar(session.name, session.avatarUrl, 64.dp)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileStat(vm.accounts.size.toString(), "Tags")
                    ProfileStat(compactCount(activityTotal), "Activity")
                    ProfileStat(formatBytes(vm.totalUploadedBytes), "Size")
                    val pinned = vm.pinnedBadgeIds.mapNotNull { id -> vm.earnedBadges.firstOrNull { it.id == id } }
                    if (pinned.isNotEmpty()) {
                        Row(
                            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            pinned.forEach { badge ->
                                BadgeMark(
                                    badge.id,
                                    28.dp,
                                    modifier = Modifier.clickable { showBadges = true },
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = { editProfile = true },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF1F1F1), contentColor = Color.Black,
                    ),
                ) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit profile", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ----- Settings -----
        item { SectionTitle("Settings", "Customization and app info") }

        item {
            Tile(
                Modifier
                    .fillMaxWidth()
                    .clickable { showOther = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "Other",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Other",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Linked devices, backup, updates, theme",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = { showOther = true },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Open", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Buy me a coffee Card
        item {
            Tile(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching {
                            uriHandler.openUri("http://buymeacoffee.com/rrajkumarpadmanabhan")
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFDD00)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalCafe,
                                contentDescription = "Buy me a coffee",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Buy me a coffee",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Support developer & future updates",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            runCatching {
                                uriHandler.openUri("http://buymeacoffee.com/rrajkumarpadmanabhan")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFDD00),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Support ☕", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // About Button Card
        item {
            Tile(
                Modifier
                    .fillMaxWidth()
                    .clickable { showAboutDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "About",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "About",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Track v${vm.currentAppVersion} · Details & info",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = { showAboutDialog = true },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("View Details", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { vm.refreshAccounts(force = true) },
                enabled = vm.accounts.isNotEmpty() && !vm.refreshing,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50),
            ) { Text(if (vm.refreshing) "Refreshing..." else "Refresh all tags now") }
        }

        item {
            OutlinedButton(
                onClick = { confirmSignOut = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.Logout, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign out")
            }
        }

        item {
            Text(
                "Track v${vm.currentAppVersion} · Developed by R Rajkumar padmanabhan",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (editProfile) {
        val context = LocalContext.current
        var draftName by remember { mutableStateOf(session.name) }
        var draftAvatar by remember { mutableStateOf(session.avatarUrl ?: "") }

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                val savedPath = saveAvatarFromUri(context, it, session.email)
                if (savedPath != null) {
                    draftAvatar = savedPath
                } else {
                    Toast.makeText(context, "Failed to load image file", Toast.LENGTH_SHORT).show()
                }
            }
        }

        AlertDialog(
            onDismissRequest = { editProfile = false },
            title = { Text("Edit profile") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier.clickable { photoPickerLauncher.launch("image/*") }
                    ) {
                        UserAvatar(
                            name = draftName,
                            avatarUrl = draftAvatar.ifBlank { null },
                            size = 80.dp
                        )
                    }

                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (draftAvatar.isNotBlank()) "Change photo (JPG/PNG)" else "Choose photo (JPG/PNG)")
                    }

                    if (draftAvatar.isNotBlank()) {
                        TextButton(onClick = { draftAvatar = "" }) {
                            Text("Remove photo", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
                    }

                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateProfile(draftName, draftAvatar)
                    editProfile = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editProfile = false }) { Text("Cancel") } },
        )
    }

    val accToRemove = removing
    if (accToRemove != null) {
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove tag?") },
            text = {
                Text("${accToRemove.platform.label} @${accToRemove.handle} and its saved daily history will be removed from your home page.")
            },
            confirmButton = {
                TextButton(onClick = { vm.removeAccount(accToRemove.id); removing = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your tags stay on this phone. Download a backup first if you want them after you sign in again.") },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            icon = {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("App Theme Color", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Select your favorite accent color to personalize the app theme.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ThemeColorOption.entries.forEach { option ->
                            val isSelected = vm.selectedThemeColor == option
                            FilterChip(
                                selected = isSelected,
                                onClick = { vm.updateThemeColor(option) },
                                label = { Text(option.label) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(option.color)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = option.color.copy(alpha = 0.25f),
                                    selectedLabelColor = Color.White,
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showOther) {
        AlertDialog(
            onDismissRequest = { showOther = false },
            title = { Text("Other", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OtherAction("Linked devices", "Open this app on a computer") {
                        showOther = false
                        vm.screen = Screen.LINKED_DEVICES
                    }
                    OtherAction("Download backup", "Save your tags and files") {
                        saveBackup.launch("TrackMe-backup.zip")
                    }
                    OtherAction("Restore backup", "Load a saved backup") {
                        openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                    OtherAction(
                        "App update",
                        if (vm.isCheckingAppUpdate) "Checking…" else "Installed version: v${vm.currentAppVersion}",
                    ) { vm.checkAppUpdate() }
                    OtherAction("Theme", "Current: ${vm.selectedThemeColor.label}") {
                        showOther = false
                        showThemeDialog = true
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOther = false }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("About Track", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "v${vm.currentAppVersion}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Developed by R Rajkumar padmanabhan",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Track keeps the public counts from the links you add, the files you upload, and a news page in one place.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Features", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    AboutBullet("Scan GitHub, GitLab, Instagram, LinkedIn, YouTube, Facebook, X, and any other website")
                    AboutBullet("One YouTube key for every channel. It is deleted when the last YouTube channel is removed")
                    AboutBullet("Upload files and see the total size on your profile")
                    AboutBullet("News stays in date order and refreshes while that page is open")
                    AboutBullet("Swipe left for news, then your files. Swipe right to go back")
                    AboutBullet("Link a computer and open Track on the desktop")
                    AboutBullet("Download or restore a backup of your tags and files")
                    AboutBullet("Turn a link into an app shortcut on the home screen")
                    AboutBullet("Badges for scans, uploads, activity, file size, and days in the app")
                    AboutBullet("Activity color and the jumping monkey follow your counts")
                    AboutBullet("Theme colors, and a blue tick after the promo code")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val updateMsg = vm.appUpdateMessage
    if (updateMsg != null) {
        AlertDialog(
            onDismissRequest = { vm.appUpdateMessage = null },
            icon = {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("App Update Status", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    updateMsg,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                if (vm.appUpdateDownloadUrl != null) {
                    Button(onClick = {
                        val url = vm.appUpdateDownloadUrl!!
                        vm.appUpdateMessage = null
                        runCatching { uriHandler.openUri(url) }
                    }) {
                        Text("Download Update", fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(onClick = { vm.appUpdateMessage = null }) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (vm.appUpdateDownloadUrl != null) {
                    TextButton(onClick = { vm.appUpdateMessage = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
    if (showCode && !vm.verified) {
        AlertDialog(
            onDismissRequest = { showCode = false },
            title = { Text("Verification", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a promo code.", fontSize = 14.sp)
                    OutlinedTextField(
                        value = promoCode,
                        onValueChange = {
                            promoCode = it
                            promoError = false
                        },
                        singleLine = true,
                        label = { Text("Promo code") },
                    )
                    if (promoError) {
                        Text("That code does not match.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (vm.redeemCode(promoCode)) showCode = false else promoError = true
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showCode = false }) { Text("Cancel") }
            },
        )
    }
    if (showBadges) {
        val activity = combinedActivity(vm.accounts).values.sum().toLong() +
            vm.accounts.filter { !it.isHidden }.sumOf { it.count ?: 0L }
        AchievementsDialog(
            earned = vm.earnedBadges,
            pinned = vm.pinnedBadgeIds,
            activity = activity,
            onTogglePin = { vm.togglePinnedBadge(it) },
            onDismiss = { showBadges = false },
        )
    }
}

@Composable
private fun AboutBullet(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column {
        Text(
            value,
            fontSize = if (value.length > 6) 16.sp else 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun OtherAction(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TagRow(acc: TrackedAccount, onRemove: () -> Unit) {
    Tile(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlatformBadge(acc.platform, 44.dp, filled = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    acc.name.ifBlank { acc.handle },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    acc.tagLine(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove tag", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

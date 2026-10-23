@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.trackme.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.TrackViewModel
import com.trackme.app.auth.GoogleAuthManager
import com.trackme.app.data.PdfPortfolio
import com.trackme.app.data.Platform
import com.trackme.app.data.TrackedAccount
import com.trackme.app.data.detectPlatform
import com.trackme.app.data.nameFromLink
import com.trackme.app.data.compactCount
import com.trackme.app.data.formatRupees
import com.trackme.app.data.parseCount
import com.trackme.app.data.tagLine
import kotlinx.coroutines.launch

/**
 * "Scan a tag": pick a platform, paste a profile link, scan, then add the result to the home page.
 */
@Composable
fun AddTagSheet(vm: TrackViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = vm.platform
    val shop = p == Platform.AMAZON || p == Platform.FLIPKART
    val draft = vm.currentDraft()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.onFileSelected(ctx, uri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { vm.closeAdd() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF111111),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCodeScanner, null, tint = Brand.Coral, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text("Scan a tag", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Pick a platform, paste the profile link, then add it to your home page.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                enabled = !vm.pdfScanning,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50),
            ) {
                if (vm.pdfScanning) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Saving…", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Upload a file", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "Up to 15 MB. Any file type. It is compressed on this phone without changing the original data or picture quality, stored under a private name, and not opened or run.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Platform.values().filter { it != Platform.AMAZON && it != Platform.FLIPKART }.forEach { option ->
                    FilterChip(
                        selected = p == option,
                        onClick = { vm.selectPlatform(option) },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = option.cardColor,
                            selectedLabelColor = Color.Black,
                        ),
                    )
                }
            }

            OutlinedTextField(
                value = vm.input,
                onValueChange = {
                    vm.input = it
                    vm.suggestAppName(it)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                label = { Text("${p.label} profile link") },
                placeholder = { Text(p.example) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { vm.scan() }),
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Make as app", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "A shortcut only. It keeps this link and name, and does not scan the page.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = vm.makeAsApp,
                    onCheckedChange = { vm.updateMakeAsApp(it) },
                )
            }
            if (vm.makeAsApp) {
                OutlinedTextField(
                    value = vm.appName,
                    onValueChange = { vm.onAppNameChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    label = { Text("App name") },
                    placeholder = { Text(nameFromLink(vm.input).ifBlank { "From the link" }) },
                )
            }

            if (!vm.makeAsApp && p == Platform.YOUTUBE) {
                OutlinedTextField(
                    value = vm.youtubeKey,
                    onValueChange = { vm.updateYoutubeKey(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    label = { Text("YouTube API key (free, from Google Cloud)") },
                )
            }
            if (!vm.makeAsApp) p.note?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (vm.makeAsApp) {
                AppDraftPreview(
                    vm.appName.ifBlank { nameFromLink(vm.input).ifBlank { "App" } },
                    (detectPlatform(vm.input) ?: p).cardColor,
                )
                Button(
                    onClick = {
                        if (vm.addDummyApp()) {
                            Toast.makeText(ctx, "Added to your app row", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.Coral, contentColor = Color.Black),
                ) {
                    Text("Add app", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!vm.makeAsApp) Button(
                onClick = { vm.scan() },
                enabled = !vm.loading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Coral, contentColor = Color.Black),
            ) {
                if (vm.loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Color.Black)
                } else {
                    Text("Scan", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            if (!vm.makeAsApp && draft != null && vm.error == null && !vm.loading) {
                if (vm.pdfPortfolio != null) {
                    PdfPortfolioPreview(vm.pdfPortfolio!!)
                } else {
                    DraftCard(draft)
                }
                vm.previewNote?.let {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val tracked = vm.isTracked(draft)
                Button(
                    onClick = {
                        if (!shop) {
                            if (vm.addDraft()) {
                                Toast.makeText(
                                    ctx,
                                    if (tracked) "Updated on your home page" else "Added to your home page",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            return@Button
                        }
                        scope.launch {
                            val email = GoogleAuthManager.signInWithGoogle(ctx).getOrNull()?.email
                                ?.trim()?.lowercase()?.takeIf { it.contains("@") }
                            if (email == null) {
                                Toast.makeText(
                                    ctx,
                                    "Choose the Google email you use for ${p.label}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return@launch
                            }
                            if (vm.addDraft(email)) {
                                Toast.makeText(
                                    ctx,
                                    if (tracked) "Updated for $email" else "Added for $email",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    Text(
                        when {
                            shop && tracked -> "Choose email to update"
                            shop -> "Choose email"
                            tracked && vm.makeAsApp -> "Update app"
                            vm.makeAsApp -> "Add app"
                            tracked -> "Update on home"
                            else -> "Add to home"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppDraftPreview(name: String, color: Color) {
    val letter = name.trim().firstOrNull()?.uppercase() ?: "A"
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, color = Color.Black, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(name.ifBlank { "App" }, fontWeight = FontWeight.SemiBold)
    }
}

/** What the scan found, in the same colours the tag will have on the home page. */
@Composable
private fun DraftCard(d: TrackedAccount) {
    val ink = Brand.Ink
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(d.platform.cardColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val img = d.avatarUrl
            if (!img.isNullOrBlank()) RemoteAvatar(img, 48) else PlatformBadge(d.platform, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    d.name.ifBlank { d.handle },
                    color = ink, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    d.tagLine(),
                    color = ink.copy(alpha = 0.65f), fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val shop = d.platform == Platform.AMAZON || d.platform == Platform.FLIPKART
        val social = !shop && !d.platform.fullScan && d.platform != Platform.YOUTUBE
        val followers = d.count?.takeIf { it > 0L }
        val following = d.extras.firstOrNull { it.first.equals("Following", true) }?.second
            ?.let { raw -> parseCount(raw)?.takeIf { it > 0L }?.let { compactCount(it) } }
        val otherExtras = if (social) emptyList() else d.extras.take(2).mapNotNull { (label, value) ->
            if (label == "Following") return@mapNotNull null
            if (value.isBlank() || value == "0") return@mapNotNull null
            label to value
        }
        if (followers != null || (social && following != null) || otherExtras.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Bottom) {
                if (followers != null) {
                    Column(modifier = Modifier.padding(start = 20.dp, top = 8.dp)) {
                        Text(
                            text = if (shop) formatRupees(followers) else compactCount(followers),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 36.sp
                            ),
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = d.countLabel.ifBlank { "Followers" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                    }
                } else if (following != null && social) {
                    Column(modifier = Modifier.padding(start = 20.dp, top = 8.dp)) {
                        Text(following, color = ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Following", color = ink.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
                if (social && followers != null && following != null) {
                    Column {
                        Text(following, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Following", color = ink.copy(alpha = 0.65f), fontSize = 12.sp)
                    }
                }
                otherExtras.forEach { (label, value) ->
                    Column {
                        Text(value, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(label, color = ink.copy(alpha = 0.65f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** The portfolio read from the PDF, grouped by the headings inside that PDF. */
@Composable
private fun PdfPortfolioPreview(portfolio: PdfPortfolio) {
    val ink = Brand.Ink
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Platform.PORTFOLIO.cardColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(portfolio.name, color = ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            portfolio.headline?.let {
                Text(it, color = ink.copy(alpha = 0.75f), fontSize = 14.sp)
            }
            val contact = listOfNotNull(portfolio.email, portfolio.phone).joinToString(" · ")
            if (contact.isNotBlank()) {
                Text(contact, color = ink.copy(alpha = 0.65f), fontSize = 12.sp)
            }
            Text(
                "${portfolio.fileName} · ${portfolio.pageCount} page${if (portfolio.pageCount == 1) "" else "s"} · ${portfolio.sections.size} sections",
                color = ink.copy(alpha = 0.65f),
                fontSize = 12.sp,
            )
            if (portfolio.usedOcr) {
                Text(
                    "This PDF had no text layer, so its pages were read as images.",
                    color = ink.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                )
            }
        }
        portfolio.sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    section.heading,
                    color = ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Text(
                    section.category,
                    color = ink.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                )
                section.items.take(6).forEach { item ->
                    Text("• $item", color = ink.copy(alpha = 0.85f), fontSize = 13.sp)
                }
                if (section.items.size > 6) {
                    Text(
                        "+ ${section.items.size - 6} more lines in this section",
                        color = ink.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

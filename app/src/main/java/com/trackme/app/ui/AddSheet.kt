@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.trackme.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.trackme.app.data.Platform
import com.trackme.app.data.TrackedAccount
import com.trackme.app.data.compactCount
import com.trackme.app.data.parseCount

/**
 * "Scan a tag": pick a platform, paste a profile link, scan, then add the result to the home page.
 */
@Composable
fun AddTagSheet(vm: TrackViewModel) {
    val ctx = LocalContext.current
    val p = vm.platform
    val draft = vm.currentDraft()

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

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Platform.values().forEach { option ->
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
                onValueChange = { vm.input = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                label = { Text("${p.label} profile link") },
                placeholder = { Text(p.example) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { vm.scan() }),
            )

            if (p == Platform.YOUTUBE) {
                OutlinedTextField(
                    value = vm.youtubeKey,
                    onValueChange = { vm.updateYoutubeKey(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    label = { Text("YouTube API key (free, from Google Cloud)") },
                )
            }
            p.note?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(
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

            if (draft != null && vm.error == null && !vm.loading) {
                DraftCard(draft)
                vm.previewNote?.let {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val tracked = vm.isTracked(draft)
                Button(
                    onClick = {
                        if (vm.addDraft()) {
                            Toast.makeText(
                                ctx,
                                if (tracked) "Updated on your home page" else "Added to your home page",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    Text(
                        if (tracked) "Update on home" else "Add to home",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
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
                    "${d.platform.label} · @${d.handle}",
                    color = ink.copy(alpha = 0.65f), fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val social = !d.platform.fullScan && d.platform != Platform.YOUTUBE
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
                            text = compactCount(followers),
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

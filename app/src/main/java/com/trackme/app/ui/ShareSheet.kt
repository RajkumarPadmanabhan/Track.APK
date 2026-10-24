package com.trackme.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.TrackViewModel

/** Hold the scan button. Send a profile, a file, or a note with a code that works off Wi-Fi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(vm: TrackViewModel) {
    val ctx = LocalContext.current
    val me = vm.session?.email.orEmpty()
    LaunchedEffect(vm.shareMessage) {
        val text = vm.shareMessage ?: return@LaunchedEffect
        vm.consumeShareMessage()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    ModalBottomSheet(
        onDismissRequest = { vm.closeShare() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text("Share", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Works on any network. The other person holds the scan button and enters the code.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            if (vm.shareBusy) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
            vm.shareStatus?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = { vm.shareProfile() },
                enabled = !vm.shareBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Share my profile") }
            if (vm.uploadedFiles.isEmpty()) {
                Text("No files to share yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            } else {
                Text("Files", fontWeight = FontWeight.SemiBold)
                vm.uploadedFiles.forEach { file ->
                    Text(
                        file.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !vm.shareBusy) { vm.shareFile(file) }
                            .padding(vertical = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            OutlinedTextField(
                value = vm.shareDraft,
                onValueChange = { vm.shareDraft = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("A note") },
                minLines = 2,
            )
            OutlinedButton(
                onClick = { vm.shareNote() },
                enabled = !vm.shareBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Share this note") }
            Text("Receive", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = vm.shareCode,
                onValueChange = { vm.shareCode = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Code") },
                singleLine = true,
            )
            Button(
                onClick = { vm.claimShare() },
                enabled = !vm.shareBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Receive") }
            Text("History", fontWeight = FontWeight.SemiBold)
            if (vm.shareHistory.isEmpty()) {
                Text("No shares yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            } else {
                vm.shareHistory.forEach { row ->
                    val who = if (row.fromEmail.equals(me, ignoreCase = true)) {
                        if (row.toEmail.isBlank()) "waiting" else "to ${row.toEmail}"
                    } else {
                        "from ${row.fromEmail}"
                    }
                    Text(
                        "${row.name} · $who",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

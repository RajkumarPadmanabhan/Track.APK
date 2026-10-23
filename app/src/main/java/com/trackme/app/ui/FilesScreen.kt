package com.trackme.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.TrackViewModel
import com.trackme.app.data.UploadedFile
import com.trackme.app.data.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FilesScreen(vm: TrackViewModel) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var isGrid by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<UploadedFile?>(null) }
    var pendingDownload by remember { mutableStateOf<UploadedFile?>(null) }

    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val file = pendingDownload
        pendingDownload = null
        if (uri != null && file != null) {
            vm.downloadUploadedFile(ctx, file, uri) { ok ->
                Toast.makeText(
                    ctx,
                    if (ok) "Downloaded ${file.displayName}" else "Download failed. The saved file could not be verified.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val files = remember(vm.uploadedFiles, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) vm.uploadedFiles else vm.uploadedFiles.filter {
            it.displayName.lowercase().contains(q) ||
                (it.portfolioName?.lowercase()?.contains(q) == true)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Files", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "${vm.uploadedFiles.size} files · ${formatBytes(vm.totalUploadedBytes)}",
                    fontSize = 13.sp,
                    color = Color.Gray,
                )
            }
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(
                    if (searching) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Search files",
                    tint = Color.White,
                )
            }
            Icon(
                Icons.Default.ViewList,
                contentDescription = "List",
                tint = if (isGrid) Color.White.copy(alpha = 0.45f) else Color.White,
                modifier = Modifier.size(22.dp).clickable { isGrid = false },
            )
            Spacer(Modifier.width(14.dp))
            Icon(
                Icons.Default.GridView,
                contentDescription = "Grid",
                tint = if (isGrid) Color.White else Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(22.dp).clickable { isGrid = true },
            )
        }
        if (searching) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                label = { Text("Search file name") },
            )
        }
        Spacer(Modifier.height(12.dp))

        if (files.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.PictureAsPdf, null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    if (vm.uploadedFiles.isEmpty()) "No files uploaded yet. Use Upload PDF in Scan."
                    else "No file matches that name.",
                    color = Color.Gray,
                )
            }
        } else if (isGrid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(files, key = { it.id }) { file ->
                    FileCard(
                        file = file,
                        compact = true,
                        onPreview = {
                            vm.previewUploadedFile(ctx, file) { ok ->
                                if (!ok) Toast.makeText(ctx, "No PDF viewer found, or the file could not be verified.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDownload = {
                            pendingDownload = file
                            downloadLauncher.launch(file.displayName)
                        },
                        onDelete = { pendingDelete = file },
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(files, key = { it.id }) { file ->
                    FileCard(
                        file = file,
                        compact = false,
                        onPreview = {
                            vm.previewUploadedFile(ctx, file) { ok ->
                                if (!ok) Toast.makeText(ctx, "No PDF viewer found, or the file could not be verified.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDownload = {
                            pendingDownload = file
                            downloadLauncher.launch(file.displayName)
                        },
                        onDelete = { pendingDelete = file },
                    )
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this file?") },
            text = { Text("${file.displayName} will be removed from this app. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteUploadedFile(ctx, file)
                    pendingDelete = null
                    Toast.makeText(ctx, "File deleted", Toast.LENGTH_SHORT).show()
                }) { Text("Delete", color = Brand.Coral) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun FileCard(
    file: UploadedFile,
    compact: Boolean,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Tile(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PictureAsPdf, null, tint = Brand.Coral, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    file.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!compact) {
                file.portfolioName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                "${formatBytes(file.originalSize)} · ${file.pageCount} pages",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
            )
            Text(
                "Saved as ${formatBytes(file.storedSize)} · ${dateText(file.uploadedAt)}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
            Row {
                IconButton(onClick = onPreview, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Visibility, contentDescription = "Preview", tint = Color.White)
                }
                IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Brand.Coral)
                }
            }
        }
    }
}

private fun dateText(millis: Long): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(millis))
}

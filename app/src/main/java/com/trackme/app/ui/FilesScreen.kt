package com.trackme.app.ui

import android.media.MediaPlayer
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
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
import com.trackme.app.data.FilePreviewer
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

    var preview by remember { mutableStateOf<FilePreviewer.Result?>(null) }
    var previewName by remember { mutableStateOf("") }
    var previewing by remember { mutableStateOf(false) }

    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
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
                Icon(Icons.Default.InsertDriveFile, null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    if (vm.uploadedFiles.isEmpty()) "No files uploaded yet. Use Upload a file in Scan."
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
                            previewName = file.displayName
                            previewing = true
                            vm.prepareFilePreview(ctx, file) { result ->
                                previewing = false
                                preview = result
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
                            previewName = file.displayName
                            previewing = true
                            vm.prepareFilePreview(ctx, file) { result ->
                                previewing = false
                                preview = result
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

    if (previewing) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Preview") },
            text = { Text("Checking the saved file…") },
            confirmButton = {},
        )
    }

    preview?.let { result ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(previewName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { PreviewBody(result) },
            confirmButton = {
                TextButton(onClick = { preview = null }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun PreviewBody(result: FilePreviewer.Result) {
    Column(
        Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        result.note?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
        when (result.kind) {
            FilePreviewer.Kind.IMAGE -> result.image?.let {
                Image(it.asImageBitmap(), contentDescription = "Preview", modifier = Modifier.fillMaxWidth())
            }
            FilePreviewer.Kind.PDF -> result.pages.forEach { page ->
                Image(page.asImageBitmap(), contentDescription = "Page", modifier = Modifier.fillMaxWidth())
            }
            FilePreviewer.Kind.TEXT -> Text(result.text.orEmpty(), color = Color.White, fontSize = 13.sp)
            FilePreviewer.Kind.VIDEO -> result.media?.let { media ->
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    factory = { viewContext ->
                        VideoView(viewContext).apply {
                            setVideoPath(media.absolutePath)
                            setOnPreparedListener { it.isLooping = false; start() }
                        }
                    },
                )
            }
            FilePreviewer.Kind.AUDIO -> AudioPreview(result.media)
            FilePreviewer.Kind.BLOCKED -> Unit
        }
    }
}

@Composable
private fun AudioPreview(file: java.io.File?) {
    if (file == null) return
    DisposableEffect(file) {
        val player = MediaPlayer()
        runCatching {
            player.setDataSource(file.absolutePath)
            player.prepare()
            player.start()
        }
        onDispose { runCatching { player.release() } }
    }
    Text("Playing the saved audio.", color = Color.White, fontSize = 13.sp)
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
                Icon(Icons.Default.InsertDriveFile, null, tint = Brand.Coral, modifier = Modifier.size(26.dp))
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
            Text(
                formatBytes(file.originalSize),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
            )
            if (!compact) {
                Text(
                    "Saved as ${formatBytes(file.storedSize)} · ${dateText(file.uploadedAt)}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
            }
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

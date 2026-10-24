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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
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

    var openFolder by remember { mutableStateOf<String?>(null) }
    var namingFolder by remember { mutableStateOf(false) }
    var folderDraft by remember { mutableStateOf("") }
    var moving by remember { mutableStateOf<UploadedFile?>(null) }
    var pendingFolderDelete by remember { mutableStateOf<String?>(null) }
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

    val files = remember(vm.uploadedFiles, query, openFolder) {
        val q = query.trim().lowercase()
        vm.uploadedFiles.filter { file ->
            val matches = q.isBlank() || file.displayName.lowercase().contains(q) ||
                (file.portfolioName?.lowercase()?.contains(q) == true)
            if (!matches) return@filter false
            if (q.isNotBlank()) true
            else if (openFolder == null) file.folder.isBlank()
            else file.folder == openFolder
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    openFolder ?: "Files",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (openFolder != null) {
                IconButton(onClick = { pendingFolderDelete = openFolder }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete folder", tint = Brand.Coral)
                }
                IconButton(onClick = { openFolder = null }) {
                    Icon(Icons.Default.Close, contentDescription = "Leave folder", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            IconButton(onClick = { folderDraft = ""; namingFolder = true }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder", tint = MaterialTheme.colorScheme.onSurface)
            }
            Icon(
                Icons.Default.ViewList,
                contentDescription = "List",
                tint = if (isGrid) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp).clickable { isGrid = false },
            )
            Spacer(Modifier.width(14.dp))
            Icon(
                Icons.Default.GridView,
                contentDescription = "Grid",
                tint = if (isGrid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
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
        if (openFolder == null && query.isBlank() && vm.fileFolders.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.fileFolders.forEach { name ->
                    val count = vm.uploadedFiles.count { it.folder == name }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { openFolder = name }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Brand.Coral)
                        Spacer(Modifier.width(10.dp))
                        Text(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        IconButton(onClick = { pendingFolderDelete = name }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete folder", tint = Brand.Coral)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (files.isEmpty() && (query.isNotBlank() || openFolder != null || vm.fileFolders.isEmpty())) {
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
                        path = filePath(file, query),
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
                        onMove = { moving = file },
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
                        path = filePath(file, query),
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
                        onMove = { moving = file },
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

    pendingFolderDelete?.let { name ->
        val count = vm.uploadedFiles.count { it.folder == name }
        AlertDialog(
            onDismissRequest = { pendingFolderDelete = null },
            title = { Text("Delete this folder?") },
            text = {
                Text(
                    if (count == 0) "$name will be removed."
                    else "$name will be removed. $count file" + (if (count == 1) "" else "s") + " will stay in Files, outside any folder.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFileFolder(name)
                    if (openFolder == name) openFolder = null
                    pendingFolderDelete = null
                    Toast.makeText(ctx, "Folder deleted", Toast.LENGTH_SHORT).show()
                }) { Text("Delete", color = Brand.Coral) }
            },
            dismissButton = {
                TextButton(onClick = { pendingFolderDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (namingFolder) {
        AlertDialog(
            onDismissRequest = { namingFolder = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = folderDraft,
                    onValueChange = { folderDraft = it },
                    singleLine = true,
                    label = { Text("Folder name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (vm.addFileFolder(folderDraft)) namingFolder = false
                    else Toast.makeText(ctx, "Choose a new folder name", Toast.LENGTH_SHORT).show()
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { namingFolder = false }) { Text("Cancel") } },
        )
    }

    moving?.let { file ->
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text("Move ${file.displayName}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { vm.moveFileToFolder(file.id, ""); moving = null }) { Text("No folder") }
                    vm.fileFolders.forEach { name ->
                        TextButton(onClick = { vm.moveFileToFolder(file.id, name); moving = null }) { Text(name) }
                    }
                    if (vm.fileFolders.isEmpty()) Text("Create a folder first, or leave this file here.", fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { moving = null }) { Text("Close") } },
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
            FilePreviewer.Kind.TEXT -> Text(result.text.orEmpty(), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
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
    Text("Playing the saved audio.", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
}

@Composable
private fun filePath(file: UploadedFile, query: String): String? {
    if (query.isBlank() || file.folder.isBlank()) return null
    return file.folder + " / " + file.displayName
}

@Composable
private fun FileCard(
    file: UploadedFile,
    path: String?,
    compact: Boolean,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
) {
    Tile(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.InsertDriveFile, null, tint = Brand.Coral, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    file.displayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (path != null) {
                Text(
                    path,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatBytes(file.originalSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            if (!compact) {
                Text(
                    "Saved as ${formatBytes(file.storedSize)} · ${dateText(file.uploadedAt)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Row {
                IconButton(onClick = onPreview, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Visibility, contentDescription = "Preview", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onMove, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = "Move to folder", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.onSurface)
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

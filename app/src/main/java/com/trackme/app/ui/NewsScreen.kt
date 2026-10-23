package com.trackme.app.ui

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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun NewsScreen(vm: com.trackme.app.TrackViewModel) {
    val newsFeed by vm.newsFeed.collectAsState()
    val isNewsLoading by vm.isNewsLoading.collectAsState()
    var isGridView by remember { mutableStateOf(false) }
    val stories = remember(newsFeed) {
        newsFeed.distinctBy { it.sourceUrl.ifBlank { it.id }.ifBlank { it.title } }
    }

    LaunchedEffect(Unit) {
        vm.loadLiveNews()
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Live News Headlines",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ViewList,
                contentDescription = "List",
                tint = if (isGridView) Color.White.copy(alpha = 0.45f) else Color.White,
                modifier = Modifier.size(22.dp).clickable { isGridView = false },
            )
            Spacer(modifier = Modifier.width(14.dp))
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = "Grid",
                tint = if (isGridView) Color.White else Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(22.dp).clickable { isGridView = true },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (isNewsLoading && stories.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Brand.Coral)
            }
        } else if (stories.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No live headlines right now.", color = Color.Gray)
            }
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(stories, key = { index, post -> "grid-$index-${post.sourceUrl}-${post.title}" }) { _, post ->
                    NewsPostCard(post = post)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(stories, key = { index, post -> "list-$index-${post.sourceUrl}-${post.title}" }) { _, post ->
                    NewsPostCard(post = post)
                }
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }
}

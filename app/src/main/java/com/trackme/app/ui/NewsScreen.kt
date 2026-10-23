package com.trackme.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.trackme.app.TrackViewModel
import com.trackme.app.data.Platform

@Composable
fun NewsScreen(vm: TrackViewModel) {
    val newsFeed by vm.newsFeed.collectAsState()
    val isNewsLoading by vm.isNewsLoading.collectAsState()
    
    val allPlatforms = Platform.values().toList()
    var selectedPlatforms by remember { mutableStateOf(allPlatforms.toSet()) }
    var isGridView by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPlatforms) {
        if (selectedPlatforms.isNotEmpty()) {
            vm.loadPersonalizedNews(selectedPlatforms.toList())
        }
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Live News Headlines",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Preference Chips
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allPlatforms) { platform ->
                val isSelected = selectedPlatforms.contains(platform)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedPlatforms = if (isSelected) {
                            if (selectedPlatforms.size > 1) {
                                selectedPlatforms - platform
                            } else selectedPlatforms
                        } else {
                            selectedPlatforms + platform
                        }
                    },
                    label = { Text(platform.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Brand.Coral,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        // View Toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            FilterChip(
                selected = !isGridView,
                onClick = { isGridView = false },
                label = { Text("List View") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Brand.Coral,
                    selectedLabelColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = isGridView,
                onClick = { isGridView = true },
                label = { Text("Grid View") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Brand.Coral,
                    selectedLabelColor = Color.White
                )
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        if (isNewsLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Brand.Coral)
            }
        } else if (newsFeed.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No news available for selected platforms.", color = Color.Gray)
            }
        } else {
            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(newsFeed.distinctBy { it.id }, key = { "news-${it.id}" }) { post ->
                        NewsPostCard(post = post)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(newsFeed.distinctBy { it.id }, key = { "news-${it.id}" }) { post ->
                        NewsPostCard(post = post)
                    }
                    item { Spacer(modifier = Modifier.height(100.dp)) }
                }
            }
        }
    }
}

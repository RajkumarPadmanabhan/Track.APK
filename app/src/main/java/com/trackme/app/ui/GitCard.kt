package com.trackme.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trackme.app.data.GitContributionDay
import com.trackme.app.data.GitDeveloperStats

@Composable
fun GitDeveloperCard(
    stats: GitDeveloperStats,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)), // Light mint-green background
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@${stats.username} (Git Activity)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B5E20)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            // Secondary Stats: Events Today, Open PRs, Open Issues
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GitStatItem(label = "Events Today", count = stats.eventsToday.toString())
                GitStatItem(label = "Open PRs", count = stats.openPRs.toString())
                GitStatItem(label = "Open Issues", count = stats.openIssues.toString())
            }
            Spacer(modifier = Modifier.height(18.dp))
            // Mini Contribution Heatmap Header
            Text(
                text = "Contribution Activity",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Heatmap Grid: 7 rows (days of week) x 7 columns (weeks)
            GitContributionGrid(contributions = stats.recentContributions)
        }
    }
}

@Composable
private fun GitStatItem(label: String, count: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B5E20)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF4CAF50)
        )
    }
}

@Composable
fun GitContributionGrid(contributions: List<GitContributionDay>) {
    // Fill up to 49 days (7 rows x 7 columns)
    val displayDays = contributions.takeLast(49).let { list ->
        if (list.size < 49) {
            List(49 - list.size) { GitContributionDay("", 0) } + list
        } else list
    }
    // 7 columns of 7 days
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (col in 0 until 7) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                for (row in 0 until 7) {
                    val index = col * 7 + row
                    val count = displayDays.getOrNull(index)?.count ?: 0
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(
                                color = getHeatmapColor(count),
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }
    }
}

// GitHub-style contribution intensity colors
private fun getHeatmapColor(count: Int): Color {
    return when {
        count == 0 -> Color(0xFFC8E6C9).copy(alpha = 0.5f)
        count in 1..2 -> Color(0xFF81C784)
        count in 3..5 -> Color(0xFF43A047)
        else -> Color(0xFF1B5E20)
    }
}

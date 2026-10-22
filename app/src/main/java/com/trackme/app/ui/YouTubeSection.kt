package com.trackme.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.data.PlatformData
import com.trackme.app.data.YouTubeVideoItem
import com.trackme.app.data.formatMetricCount

@Composable
fun YouTubeDetailSection(data: PlatformData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${data.username} (YouTube)",
                style = MaterialTheme.typography.titleMedium
            )
            Column(
                modifier = Modifier.padding(start = 20.dp, top = 8.dp)
            ) {
                val formattedCount = formatMetricCount(data.followersOrSubs)
                val metricLabel = "Subscribers"
                Text(
                    text = formattedCount,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 36.sp
                    ),
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = metricLabel,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.Black.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Subscriber Trend Graph
            Text("Growth Over Time", style = MaterialTheme.typography.titleSmall)
            SimpleBarChart(data.historicalMetrics)
            Spacer(modifier = Modifier.height(16.dp))
            // Recent Videos Horizontal Carousel
            Text("Recent Videos", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.recentVideos) { video ->
                    VideoItemCard(video)
                }
            }
        }
    }
}

@Composable
fun VideoItemCard(video: YouTubeVideoItem) {
    ElevatedCard(modifier = Modifier.width(180.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = video.title, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            Text(text = video.publishedAt, style = MaterialTheme.typography.labelSmall)
            if (video.viewCount > 0) {
                Text(
                    text = "${formatMetricCount(video.viewCount)} views",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

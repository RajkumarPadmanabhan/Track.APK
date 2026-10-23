package com.trackme.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trackme.app.data.UserNewsPost

@Composable
fun NewsPostCard(post: UserNewsPost) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable {
                DeepLinkHelper.openPlatformUrl(context, post.sourceUrl)
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Category Pill & Source Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = getTagColor(post.categoryTag)
                ) {
                    Text(
                        text = post.categoryTag,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "${post.sourceName} • ${post.publishedAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Thumbnail Image (if provided)
            if (!post.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            // Headline
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // Short Description
            if (post.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = post.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun getTagColor(tag: String): Color {
    return when (tag) {
        "YouTube" -> Color(0xFFFFCDD2)   // Soft Red
        "Instagram" -> Color(0xFFFFE0B2) // Warm Orange
        "LinkedIn" -> Color(0xFFBBDEFB)  // Light Blue
        "Dev" -> Color(0xFFC8E6C9)       // Mint Green
        else -> Color(0xFFE0E0E0)
    }
}

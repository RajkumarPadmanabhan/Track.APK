package com.trackme.app.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeColorOption(val label: String, val color: Color) {
    CORAL("Coral Pink", Color(0xFFFF4D6D)),
    EMERALD("Emerald Teal", Color(0xFF10B981)),
    ELECTRIC_BLUE("Electric Blue", Color(0xFF3B82F6)),
    AMBER("Amber Gold", Color(0xFFF59E0B)),
    VIOLET("Violet Purple", Color(0xFF8B5CF6)),
    CYAN("Cyan Neon", Color(0xFF06B6D4));

    companion object {
        fun fromName(name: String): ThemeColorOption =
            entries.firstOrNull { it.name == name } ?: CORAL
    }
}

@Composable
fun TrackTheme(accentColor: Color = Color(0xFFFF4D6D), content: @Composable () -> Unit) {
    val trackColors = darkColorScheme(
        primary = accentColor,
        onPrimary = Color.Black,
        background = Color.Black,
        onBackground = Color.White,
        surface = Color(0xFF141414),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF262626),
        onSurfaceVariant = Color(0xFF9A9A9A),
        secondaryContainer = accentColor.copy(alpha = 0.25f),
        onSecondaryContainer = Color.White,
        outline = Color(0xFF3A3A3A),
        error = Color(0xFFFF6B6B),
    )
    MaterialTheme(colorScheme = trackColors, content = content)
}

/** Rounded dark "bento" tile used for every card in the app. */
@Composable
fun Tile(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        content = content,
    )
}

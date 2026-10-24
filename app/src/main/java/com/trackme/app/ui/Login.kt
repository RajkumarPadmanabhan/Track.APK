package com.trackme.app.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.TrackViewModel
import com.trackme.app.auth.GoogleAuthManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(vm: TrackViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }

    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    val wave by rememberInfiniteTransition(label = "open").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "openWave",
    )
    val headline = ((intro.value - 0.08f) / 0.42f).coerceIn(0f, 1f)
    val buttonIn = ((intro.value - 0.42f) / 0.58f).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        OpenWave(
            wave,
            headline,
            Modifier.fillMaxWidth().height(220.dp).align(Alignment.Center).padding(horizontal = 8.dp),
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer { alpha = intro.value },
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Brand.Coral))
                Spacer(Modifier.width(8.dp))
                Text("TrackMe", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
            }

            Spacer(Modifier.weight(1f))

            Text(
                "Track your",
                fontSize = 40.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    alpha = headline
                    translationY = (1f - headline) * 36f
                },
            )
            Text(
                "track records",
                fontSize = 40.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Brand.Coral,
                modifier = Modifier.graphicsLayer {
                    alpha = headline
                    translationY = (1f - headline) * 48f
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Scan your profiles once. See followers, subscribers and commits move every day, all on one home page.",
                fontSize = 15.sp,
                color = Color(0xFFD0D0D0),
                modifier = Modifier.graphicsLayer { alpha = headline },
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (!isSigningIn) {
                        isSigningIn = true
                        scope.launch {
                            val result = GoogleAuthManager.signInWithGoogle(context)
                            isSigningIn = false
                            result.onSuccess { user ->
                                Toast.makeText(context, "Welcome, ${user.displayName ?: user.email}!", Toast.LENGTH_SHORT).show()
                                vm.signInWithGoogleUser(user)
                            }.onFailure { error ->
                                Toast.makeText(context, "Google Sign-In failed: ${error.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = !isSigningIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .graphicsLayer {
                        alpha = buttonIn
                        translationY = (1f - buttonIn) * 28f
                    },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) {
                if (isSigningIn) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.Black
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Signing in...", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(Color(0xFFF1F3F4)),
                        contentAlignment = Alignment.Center,
                    ) { Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    Spacer(Modifier.width(12.dp))
                    Text("Continue with Google", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Your tags and counts are stored only on this device.",
                fontSize = 12.sp,
                color = Color(0xFFBDBDBD),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = buttonIn },
            )
        }
    }
}

@Composable
private fun OpenWave(wave: Float, fade: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (size.width < 2f || fade <= 0f) return@Canvas
        val n = 28
        val path = Path()
        for (i in 0..n) {
            val x = size.width * i / n
            val t = i / n.toFloat()
            val y = size.height * 0.55f +
                kotlin.math.sin((t * 6.2f + wave * 6.28f).toDouble()).toFloat() * size.height * 0.22f +
                kotlin.math.sin((t * 2.4f + wave * 3f).toDouble()).toFloat() * size.height * 0.08f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            Brand.Coral.copy(alpha = 0.55f * fade),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

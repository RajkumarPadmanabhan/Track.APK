package com.trackme.app.ui

import android.widget.Toast
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Brand.Coral))
                Spacer(Modifier.width(8.dp))
                Text("TrackMe", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }

            Spacer(Modifier.weight(1f))

            Text(
                buildAnnotatedString {
                    append("Your socials and\ngit activity, ")
                    withStyle(SpanStyle(color = Brand.Coral)) { append("tracked daily.") }
                },
                fontSize = 36.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Scan your profiles once. See followers, subscribers and commits move every day, all on one home page.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                modifier = Modifier.fillMaxWidth().height(58.dp),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

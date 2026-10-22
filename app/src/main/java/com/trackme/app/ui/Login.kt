@file:OptIn(ExperimentalMaterial3Api::class)

package com.trackme.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.TrackViewModel

@Composable
fun LoginScreen(vm: TrackViewModel) {
    var showEmail by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Brand.Coral))
                Spacer(Modifier.width(8.dp))
                Text("TrackMe", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }

            Spacer(Modifier.height(20.dp))
            Collage()

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
                onClick = { showEmail = true },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color(0xFFF1F3F4)),
                    contentAlignment = Alignment.Center,
                ) { Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                Spacer(Modifier.width(12.dp))
                Text("Continue with Google", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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

    if (showEmail) EmailSheet(vm) { showEmail = false }
}

/** Three tilted sample cards, a preview of the home page. */
@Composable
private fun Collage() {
    BoxWithConstraints(Modifier.fillMaxWidth().height(250.dp)) {
        val w = maxWidth
        SampleCard("Followers", "12.4K", "+128 today", Color(0xFFCBF26E), -6f, w * 0.52f, Modifier.offset(0.dp, 34.dp))
        SampleCard("Git events", "24", "7-day streak", Color(0xFFD8CCFF), 5f, w * 0.5f, Modifier.offset(w * 0.47f, 0.dp))
        SampleCard("Subscribers", "3,208", "+14 today", Color(0xFFF7E04D), -3f, w * 0.56f, Modifier.offset(w * 0.2f, 128.dp))
    }
}

@Composable
private fun SampleCard(
    label: String,
    value: String,
    footer: String,
    color: Color,
    angle: Float,
    cardWidth: Dp,
    modifier: Modifier,
) {
    Column(
        modifier
            .rotate(angle)
            .width(cardWidth)
            .height(112.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(color)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Brand.Ink.copy(alpha = 0.65f), fontSize = 13.sp)
        Text(value, color = Brand.Ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text(footer, color = Brand.Ink.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmailSheet(vm: TrackViewModel, onDismiss: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val submit = {
        if (!vm.signIn(email)) error = "Enter a valid email address, like name@gmail.com."
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF111111),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Enter your email", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "Use the email you sign in to Google with. TrackMe creates your profile from it.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                label = { Text("Email") },
                placeholder = { Text("name@gmail.com") },
                isError = error != null,
                supportingText = { error?.let { msg -> Text(msg) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Button(
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Coral, contentColor = Color.Black),
            ) { Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

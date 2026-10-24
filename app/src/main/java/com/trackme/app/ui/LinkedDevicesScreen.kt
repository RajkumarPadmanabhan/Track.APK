package com.trackme.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackme.app.TrackViewModel
import com.trackme.app.data.DesktopLink

@Composable
fun LinkedDevicesScreen(vm: TrackViewModel) {
    val context = LocalContext.current
    val clients by DesktopLink.clients.collectAsState()
    LaunchedEffect(Unit) { vm.startDesktop() }
    val url = vm.desktopUrl
    val qr = remember(url) { url?.let { DesktopLink.qrBitmap(it) }?.asImageBitmap() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Linked Devices", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(
            "Put the phone and the computer on the same Wi-Fi. Open the address, type the code, then use Install TrackWindows. That downloads TrackWindows.cmd, separate from the phone APK. Open it to get TrackWindows.exe. Log out here, or sign out of the app, and the computer drops the link immediately.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))
        if (vm.desktopError != null) {
            Text(vm.desktopError ?: "", color = Color(0xFFFF8A80))
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.startDesktop() }) { Text("Try again") }
        } else if (url == null) {
            Button(
                onClick = { vm.startDesktop() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A80), contentColor = Color.Black)
            ) { Text("Show the address", fontWeight = FontWeight.Bold) }
        } else if (url != null && qr != null) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Code", color = Color(0xFF666666), fontSize = 12.sp)
                    Text(
                        vm.desktopCode ?: "",
                        color = Color.Black,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Image(bitmap = qr, contentDescription = "QR code for this phone", modifier = Modifier.size(180.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Scan this with the computer camera, or type the address and the code.",
                        color = Color(0xFF333333),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Type this address", color = Color.Gray, fontSize = 12.sp)
                        Text(url.substringBefore("?"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Track", url.substringBefore("?")))
                        Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy address", tint = Color.LightGray)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Connected (${clients.size})", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            if (url != null) {
                TextButton(onClick = { vm.stopDesktop(); vm.screen = com.trackme.app.Screen.PROFILE }) {
                    Text("Log out", color = Color(0xFFFF5252))
                }
            }
        }
        if (clients.isEmpty()) {
            Text("Waiting for a computer…", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        } else {
            clients.forEach { session ->
                Spacer(Modifier.height(10.dp))
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).background(Color(0xFF2A2A36), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Computer, null, tint = Color(0xFFFF8A80))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${session.browser} · ${session.system}", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Since ${session.since}", color = Color.Gray, fontSize = 12.sp)
                        }
                        IconButton(onClick = { vm.stopDesktop() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Disconnect", tint = Color(0xFFFF5252))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Leave this screen open while you use the computer. Log out when you are done. Only someone on this Wi-Fi who has this address can open it.",
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

package com.trackme.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WebSyncManager {
    private val _activeSessions = MutableStateFlow<List<WebSession>>(emptyList())
    val activeSessions: StateFlow<List<WebSession>> = _activeSessions.asStateFlow()

    // Pairs via QR code text or 6-digit alphanumeric code (e.g. TRK-8492)
    fun pairWithWeb(codeOrToken: String, userLocation: String = "Thiruvananthapuram, India"): Boolean {
        if (codeOrToken.isBlank()) return false
        val currentTime = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
        val newSession = WebSession(
            sessionId = codeOrToken,
            browserName = "Chrome Desktop",
            operatingSystem = "Windows 11 / macOS",
            ipAddress = "157.45.198.24",
            location = userLocation,
            loginTime = currentTime,
            isCurrentActive = true
        )
        _activeSessions.value = _activeSessions.value + newSession
        return true
    }

    // Terminate session remotely from the mobile phone
    fun revokeSession(sessionId: String) {
        _activeSessions.value = _activeSessions.value.filterNot { it.sessionId == sessionId }
    }

    fun revokeAllSessions() {
        _activeSessions.value = emptyList()
    }
}

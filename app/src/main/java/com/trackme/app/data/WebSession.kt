package com.trackme.app.data

data class WebSession(
    val sessionId: String,
    val browserName: String,     // e.g. "Google Chrome 124"
    val operatingSystem: String, // e.g. "Windows 11" or "macOS Sonoma"
    val ipAddress: String,       // e.g. "103.28.244.18"
    val location: String,        // e.g. "Thiruvananthapuram, India"
    val loginTime: String,       // e.g. "Today at 7:45 PM"
    val isCurrentActive: Boolean = true
)

package com.trackme.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

object DeepLinkHelper {
    fun openPlatformUrl(context: Context, urlString: String) {
        val cleanUrl = if (!urlString.startsWith("http://", ignoreCase = true) && !urlString.startsWith("https://", ignoreCase = true)) {
            "https://$urlString"
        } else urlString
        val uri = Uri.parse(cleanUrl)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Try opening with targeted package first if recognized
        val packageName = when {
            cleanUrl.contains("youtube.com") || cleanUrl.contains("youtu.be") -> "com.google.android.youtube"
            cleanUrl.contains("linkedin.com") -> "com.linkedin.android"
            cleanUrl.contains("instagram.com") -> "com.instagram.android"
            cleanUrl.contains("facebook.com") -> "com.facebook.katana"
            cleanUrl.contains("github.com") -> "com.github.android"
            else -> null
        }
        if (packageName != null) {
            val appIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(appIntent)
                return
            } catch (e: Exception) {
                // App not installed, fallback to browser
            }
        }
        // Default browser intent
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

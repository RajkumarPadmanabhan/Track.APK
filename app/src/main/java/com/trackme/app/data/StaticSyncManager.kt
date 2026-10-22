package com.trackme.app.data

object StaticSyncManager {
    data class PlatformExportItem(
        val platform: String,
        val username: String,
        val metric: String,
        val label: String,
        val profileUrl: String
    )

    fun exportAccounts(accounts: List<TrackedAccount>): List<PlatformExportItem> {
        return accounts.map { acc ->
            PlatformExportItem(
                platform = acc.platform.label,
                username = acc.handle,
                metric = acc.count?.let { compactCount(it) } ?: "0",
                label = acc.countLabel,
                profileUrl = acc.url
            )
        }
    }
}

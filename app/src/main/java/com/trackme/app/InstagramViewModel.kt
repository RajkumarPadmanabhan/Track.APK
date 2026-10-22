package com.trackme.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackme.app.data.InstagramProfileStats
import com.trackme.app.data.InstagramScraper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InstagramViewModel : ViewModel() {
    private val _profileStats = MutableStateFlow<InstagramProfileStats?>(null)
    val profileStats: StateFlow<InstagramProfileStats?> = _profileStats.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Called when the user pastes the URL in the Add Sheet / Scan input
    fun scanAndAddUrl(url: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = InstagramScraper.fetchCountsOnly(url)
            if (result != null) {
                _profileStats.value = result
            }
            _isRefreshing.value = false
        }
    }

    // Called on pull-to-refresh or tap refresh: re-fetches live counts from Instagram
    fun refreshStats() {
        val current = _profileStats.value ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            val updated = InstagramScraper.fetchCountsOnly(current.profileUrl)
            if (updated != null) {
                _profileStats.value = updated
            }
            _isRefreshing.value = false
        }
    }
}

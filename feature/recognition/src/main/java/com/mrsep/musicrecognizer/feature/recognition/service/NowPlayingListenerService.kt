package com.mrsep.musicrecognizer.feature.recognition.service

import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification access is the only way to observe media sessions of other apps.
 * The notifications themselves are not used.
 */
class NowPlayingListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        _isConnected.value = true
    }

    override fun onListenerDisconnected() {
        _isConnected.value = false
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)
        internal val isConnected = _isConnected.asStateFlow()
    }
}

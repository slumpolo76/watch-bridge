package com.watchbridge.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stato connessione Watch condiviso tra service e UI. */
object BLEConnectionState {
    private val _watchConnected = MutableStateFlow(false)
    val watchConnected: StateFlow<Boolean> = _watchConnected.asStateFlow()

    fun setWatchConnected(connected: Boolean) {
        _watchConnected.value = connected
    }
}

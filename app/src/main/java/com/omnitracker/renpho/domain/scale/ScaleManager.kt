package com.omnitracker.renpho.domain.scale

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class DiscoveredScale(
    val address: String,
    val name: String,
    val rssi: Int,
)

/** A final, stable reading delivered by a scale. */
data class ScaleReading(
    val weightKg: Float,
    val impedanceOhm: Int? = null,
    /** Some standard (BCS) scales compute body fat themselves. */
    val deviceBodyFatPercent: Float? = null,
)

enum class ScaleError {
    BLUETOOTH_UNAVAILABLE,
    BLUETOOTH_OFF,
    PERMISSION_DENIED,
    SCAN_FAILED,
    CONNECTION_FAILED,
    UNSUPPORTED_DEVICE,
}

sealed interface ScaleStatus {
    data object Idle : ScaleStatus
    data object Scanning : ScaleStatus
    data class Connecting(val name: String) : ScaleStatus
    /** Connected and configured, waiting for the user to step on. */
    data class Connected(val name: String) : ScaleStatus
    data class Measuring(val name: String, val liveWeightKg: Float) : ScaleStatus
    data object Disconnected : ScaleStatus
    data class Failed(val error: ScaleError) : ScaleStatus
}

interface ScaleManager {
    val status: StateFlow<ScaleStatus>
    val discoveredScales: StateFlow<List<DiscoveredScale>>
    val readings: Flow<ScaleReading>

    fun isBluetoothEnabled(): Boolean
    fun startScan()
    fun stopScan()
    fun connect(address: String)
    fun disconnect()
}

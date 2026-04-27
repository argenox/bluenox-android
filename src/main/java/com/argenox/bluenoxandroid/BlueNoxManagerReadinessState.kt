package com.argenox.bluenoxandroid

/**
 * Represents current BLE readiness for the manager.
 *
 * This combines hardware, adapter, permissions, and location-service requirements.
 */
data class BlueNoxManagerReadinessState(
    val initialized: Boolean,
    val bluetoothEnabled: Boolean,
    val permissionsGranted: Boolean,
    val locationServicesEnabled: Boolean,
    val hardwareCompatible: Boolean,
    val ready: Boolean,
) {
    companion object {
        val Uninitialized = BlueNoxManagerReadinessState(
            initialized = false,
            bluetoothEnabled = false,
            permissionsGranted = false,
            locationServicesEnabled = false,
            hardwareCompatible = false,
            ready = false,
        )
    }
}

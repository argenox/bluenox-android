package com.argenox.bluenoxandroid

/**
 * Snapshot of whether BLE operations are likely to succeed for this process.
 *
 * @property initialized [BluenoxLEManager.initialize] completed successfully.
 * @property bluetoothEnabled The default adapter is powered on.
 * @property permissionsGranted Scan (with location-from-scan) and connect runtime permissions are granted.
 * @property locationServicesEnabled System location is enabled (required for many scans on API 23+).
 * @property hardwareCompatible The device reports [PackageManager.FEATURE_BLUETOOTH_LE].
 * @property ready All of the above allow typical foreground scan + connect flows.
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
        /** Default state before [BluenoxLEManager.initialize] or after [BluenoxLEManager.uninitialize]. */
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

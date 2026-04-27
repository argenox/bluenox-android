package com.argenox.bluenoxandroid

/**
 * High-level readiness label derived from [BlueNoxManagerReadinessState].
 * Emitted by [BluenoxLEManager.readinessStatus].
 */
enum class BlueNoxManagerReadinessStatus {
    /** [BluenoxLEManager] has not been initialized or was uninitialized. */
    UNINITIALIZED,
    /** No LE hardware or feature not present. */
    BLUETOOTH_NOT_AVAILABLE,
    /** Bluetooth adapter exists but is off. */
    BLUETOOTH_NOT_ENABLED,
    /** One or more required runtime permissions are missing. */
    PERMISSIONS_NOT_GRANTED,
    /** Location services are disabled while required for scanning on this API level. */
    LOCATION_SERVICES_NOT_ENABLED,
    /** Manager is initialized and prerequisites for typical BLE use are met. */
    READY,
}

package com.argenox.bluenoxandroid

enum class BlueNoxManagerReadinessStatus {
    UNINITIALIZED,
    BLUETOOTH_NOT_AVAILABLE,
    BLUETOOTH_NOT_ENABLED,
    PERMISSIONS_NOT_GRANTED,
    LOCATION_SERVICES_NOT_ENABLED,
    READY,
}

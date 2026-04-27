package com.argenox.bluenoxandroid

enum class BlueNoxConnectionProfile {
    AGGRESSIVE,
    BALANCED,
    BATTERY_SAVER,
}

/**
 * Configures reconnect attempts and backoff strategy.
 */
data class BlueNoxConnectionPolicy(
    val maxReconnectAttempts: Int,
    val baseBackoffMs: Long,
    val maxBackoffMs: Long,
) {
    companion object {
        val Balanced = BlueNoxConnectionPolicy(
            maxReconnectAttempts = 5,
            baseBackoffMs = 250L,
            maxBackoffMs = 4_000L,
        )
    }
}

fun BlueNoxConnectionProfile.toPolicy(): BlueNoxConnectionPolicy {
    return when (this) {
        BlueNoxConnectionProfile.AGGRESSIVE -> BlueNoxConnectionPolicy(
            maxReconnectAttempts = 7,
            baseBackoffMs = 150L,
            maxBackoffMs = 2_000L,
        )

        BlueNoxConnectionProfile.BALANCED -> BlueNoxConnectionPolicy.Balanced

        BlueNoxConnectionProfile.BATTERY_SAVER -> BlueNoxConnectionPolicy(
            maxReconnectAttempts = 3,
            baseBackoffMs = 500L,
            maxBackoffMs = 8_000L,
        )
    }
}


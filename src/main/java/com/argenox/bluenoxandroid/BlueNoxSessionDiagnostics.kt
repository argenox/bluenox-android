package com.argenox.bluenoxandroid

/**
 * Lightweight runtime counters for connection/bond reliability diagnostics.
 */
data class BlueNoxSessionDiagnostics(
    val reconnectAttempts: Int,
    val reconnectExhaustions: Int,
    val bondAttempts: Int,
    val bondRetries: Int,
    val bondFailures: Int,
    val operationTimeouts: Int,
)


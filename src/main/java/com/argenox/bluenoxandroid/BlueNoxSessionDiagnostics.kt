package com.argenox.bluenoxandroid

/**
 * Lightweight runtime counters for connection/bond reliability diagnostics.
 *
 * @property reconnectAttempts Total GATT reconnect attempts scheduled for this session.
 * @property reconnectExhaustions Times reconnect backoff exhausted without success.
 * @property bondAttempts Times [BlueNoxDevice.pairAndBond] was started.
 * @property bondRetries Automatic bond retries triggered by policy.
 * @property bondFailures Bond attempts that ended in failure paths.
 * @property operationTimeouts Queued GATT operations that timed out waiting for completion.
 */
data class BlueNoxSessionDiagnostics(
    val reconnectAttempts: Int,
    val reconnectExhaustions: Int,
    val bondAttempts: Int,
    val bondRetries: Int,
    val bondFailures: Int,
    val operationTimeouts: Int,
)


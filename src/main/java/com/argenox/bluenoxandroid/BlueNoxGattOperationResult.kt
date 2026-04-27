package com.argenox.bluenoxandroid

/**
 * Outcome of a GATT-related request started on [BlueNoxDevice] (e.g. read, write, MTU).
 *
 * @property operation Short machine-readable name (e.g. `"read"`, `"write-split"`).
 * @property success Whether the stack accepted starting the operation; completion may still arrive asynchronously via callbacks.
 * @property detail Human-readable explanation or error context.
 * @property characteristicUuid Related characteristic UUID when applicable; may be null for connection-level ops.
 * @property bytes Total payload size for writes when reported.
 * @property chunksStarted Number of chunks successfully queued for split/long writes when reported.
 */
data class BlueNoxGattOperationResult(
    val operation: String,
    val success: Boolean,
    val detail: String,
    val characteristicUuid: String? = null,
    val bytes: Int? = null,
    val chunksStarted: Int? = null,
)

package com.argenox.bluenoxandroid

data class BlueNoxGattOperationResult(
    val operation: String,
    val success: Boolean,
    val detail: String,
    val characteristicUuid: String? = null,
    val bytes: Int? = null,
    val chunksStarted: Int? = null,
)

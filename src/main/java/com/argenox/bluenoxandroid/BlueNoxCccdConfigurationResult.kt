package com.argenox.bluenoxandroid

/**
 * Result of writing the Client Characteristic Configuration descriptor (0x2902) for notify/indicate.
 *
 * @property characteristicUuid Characteristic whose CCCD was written.
 * @property enabled Whether notifications or indications were requested enabled (vs disabled).
 * @property indicate True if indication mode was requested when enabling.
 * @property gattStatus Android [android.bluetooth.BluetoothGatt] status for the descriptor write.
 * @property confirmedValueMatches Whether the written descriptor value matched the expected enable/disable bytes.
 */
data class BlueNoxCccdConfigurationResult(
    val characteristicUuid: String,
    val enabled: Boolean,
    val indicate: Boolean,
    val gattStatus: Int,
    val confirmedValueMatches: Boolean,
)

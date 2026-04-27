package com.argenox.bluenoxandroid

data class BlueNoxCccdConfigurationResult(
    val characteristicUuid: String,
    val enabled: Boolean,
    val indicate: Boolean,
    val gattStatus: Int,
    val confirmedValueMatches: Boolean,
)

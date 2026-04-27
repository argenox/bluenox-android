package com.argenox.bluenoxandroid

/**
 * Configures automatic bond retry/timeout behavior for [BlueNoxDevice].
 */
data class BlueNoxBondingPolicy(
    val autoRetryEnabled: Boolean = true,
    val maxRetries: Int = 2,
    val retryBackoffMs: Long = 800L,
    val timeoutMs: Long = 15_000L,
) {
    companion object {
        val Default = BlueNoxBondingPolicy()
    }
}


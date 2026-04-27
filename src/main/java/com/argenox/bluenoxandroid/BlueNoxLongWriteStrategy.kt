package com.argenox.bluenoxandroid

/**
 * Configures behavior for long/split characteristic writes.
 *
 * This strategy controls chunk sizing and how chunk failures are handled.
 */
data class BlueNoxLongWriteStrategy(
    val batchSize: Int = 20,
    val continueOnChunkFailure: Boolean = false,
    val maxRetriesPerChunk: Int = 0,
    val retryBackoffMs: Long = 100L,
    val interChunkDelayMs: Long = 0L,
) {
    init {
        require(batchSize > 0) { "batchSize must be > 0" }
        require(maxRetriesPerChunk >= 0) { "maxRetriesPerChunk must be >= 0" }
        require(retryBackoffMs >= 0L) { "retryBackoffMs must be >= 0" }
        require(interChunkDelayMs >= 0L) { "interChunkDelayMs must be >= 0" }
    }

    companion object {
        val Default = BlueNoxLongWriteStrategy()
    }
}

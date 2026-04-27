package com.argenox.bluenoxandroid

/**
 * Configures behavior for long/split characteristic writes on [BlueNoxDevice].
 *
 * @property batchSize Maximum payload bytes per GATT write chunk.
 * @property continueOnChunkFailure If true, remaining chunks are attempted after a chunk failure; if false, the whole write stops on first failure.
 * @property maxRetriesPerChunk Extra attempts per chunk after the first failure (0 = no retries).
 * @property retryBackoffMs Delay between retry attempts for the same chunk.
 * @property interChunkDelayMs Optional pause after each successful chunk before starting the next.
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
        /** Default strategy: 20-byte chunks, no retries, no inter-chunk delay. */
        val Default = BlueNoxLongWriteStrategy()
    }
}

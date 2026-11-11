package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.KeyValue
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction

internal const val RANGE_SCAN_BATCH_SIZE = 512

/** Small queries start small; rejected candidates progressively increase read-ahead. */
internal class AdaptiveRangeBatch(limit: UInt) {
    private var size = limit.coerceIn(1u, RANGE_SCAN_BATCH_SIZE.toUInt()).toInt()

    fun nextSize(): Int = size.also { size = (size * 2).coerceAtMost(RANGE_SCAN_BATCH_SIZE) }
}

internal data class RangeBatchResult(
    val count: Int,
    val lastKey: ByteArray?,
    val completed: Boolean,
    val stoppedByCallback: Boolean = false
)

internal fun Transaction.forEachInRangeBatch(
    range: Range,
    reverse: Boolean,
    batchSize: Int = RANGE_SCAN_BATCH_SIZE,
    prepareBatch: (List<KeyValue>) -> Unit = {},
    process: (KeyValue) -> Boolean
): RangeBatchResult {
    require(batchSize > 0) { "Range batch size must be positive" }
    // Java asList already selects EXACT for a finite row limit. Keep the
    // binding's default; the local probe found no benefit from WANT_ALL.
    val batch = getRange(range, batchSize, reverse).asList().awaitResult()
    prepareBatch(batch)
    var count = 0
    var lastKey: ByteArray? = null

    for (kv in batch) {
        count++
        lastKey = kv.key
        if (!process(kv)) {
            return RangeBatchResult(count, lastKey, completed = false, stoppedByCallback = true)
        }
    }

    return RangeBatchResult(count, lastKey, completed = count < batchSize)
}

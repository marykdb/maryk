package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.KeyValue
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction

internal const val RANGE_SCAN_BATCH_SIZE = 512

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
    process: (KeyValue) -> Boolean
): RangeBatchResult {
    val batch = getRange(range, batchSize, reverse).asList().awaitResult()
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

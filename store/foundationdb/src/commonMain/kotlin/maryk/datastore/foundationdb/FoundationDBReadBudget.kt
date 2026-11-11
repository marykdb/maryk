package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import maryk.core.exceptions.RequestException
import maryk.foundationdb.KeyValue

class FoundationDBReadLimitExceededException(val limitBytes: Long) : RequestException(
    "FoundationDB read exceeds the $limitBytes-byte materialization budget; use a projection or a smaller page",
)

internal class FoundationDBReadBudget(
    private val limit: Long,
    private val parent: FoundationDBReadBudget? = null,
) {
    private val used = atomic(0L)

    fun consume(row: KeyValue) {
        val bytes = row.key.size.toLong() + row.value.size
        val total = used.addAndGet(bytes)
        if (total < 0 || total > limit) throw FoundationDBReadLimitExceededException(limit)
        parent?.consumeBytes(bytes)
    }

    fun consumeMemoizedBytes(bytes: Long) {
        require(bytes >= 0)
        if (bytes > 0) consumeBytes(bytes)
    }

    private fun consumeBytes(bytes: Long) {
        val total = used.addAndGet(bytes)
        if (total < 0 || total > limit) throw FoundationDBReadLimitExceededException(limit)
        parent?.consumeBytes(bytes)
    }
}

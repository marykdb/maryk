package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maryk.core.extensions.bytes.toULong
import maryk.core.extensions.bytes.writeBytes
import maryk.foundationdb.Range
import maryk.foundationdb.tuple.Tuple
import kotlin.uuid.Uuid

/** A committed, monotonic owner ID makes every first-attempt receipt key fresh. */
internal class OrdinaryReceiptSession(private val allocateSession: suspend () -> Long) {
    private val sessionBytes = atomic<ByteArray?>(null)
    private val sessionMutex = Mutex()
    private val sequence = atomic(0L)

    suspend fun nextKey(prefix: ByteArray): ByteArray {
        val session = sessionBytes.value ?: sessionMutex.withLock {
            sessionBytes.value ?: run {
                val owner = allocateSession()
                check(owner > 0) { "Invalid ordinary receipt owner" }
                // Version-zero UUIDs are disjoint from legacy random version-four owners.
                Tuple.from(Uuid.fromLongs(0L, owner).toString()).pack().also { sessionBytes.value = it }
            }
        }
        val ordinal = sequence.updateAndGet {
            check(it < Long.MAX_VALUE) { "Ordinary receipt sequence exhausted" }
            it + 1
        }
        return packReceiptKey(prefix, session, ordinal)
    }

    fun range(prefix: ByteArray): Range? = sessionBytes.value?.let { session ->
        Range.startsWith(ByteArray(prefix.size + session.size).also {
            prefix.copyInto(it)
            session.copyInto(it, prefix.size)
        })
    }
}

/** The counter occupies the directory's naked prefix, outside every owner's tuple range. */
internal fun advanceOrdinaryReceiptSessionCounter(previous: ByteArray?): Pair<Long, ByteArray> {
    val current = previous?.let {
        check(it.size == Long.SIZE_BYTES) { "Malformed ordinary receipt owner counter" }
        it.toULong(0, Long.SIZE_BYTES).also { value ->
            check(value in 1uL until Long.MAX_VALUE.toULong()) { "Invalid or exhausted ordinary receipt owner counter" }
        }
    } ?: 0uL
    val next = current + 1uL
    val bytes = ByteArray(Long.SIZE_BYTES)
    var index = 0
    next.writeBytes({ bytes[index++] = it }, Long.SIZE_BYTES)
    return next.toLong() to bytes
}

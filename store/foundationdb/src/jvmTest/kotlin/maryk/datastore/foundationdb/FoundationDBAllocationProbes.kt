package maryk.datastore.foundationdb

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import maryk.core.models.graph
import maryk.core.properties.types.Bytes
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import kotlin.time.measureTime
import kotlin.uuid.Uuid

private fun allocationCounter(): () -> Long {
    val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
    if (bean == null || !bean.isThreadAllocatedMemorySupported) return { -1L }
    bean.isThreadAllocatedMemoryEnabled = true
    return { bean.getThreadAllocatedBytes(bean.allThreadIds).filter { it >= 0 }.sum() }
}

/** Opt-in diagnostics, not timing assertions. Includes allocations on SDK/coroutine workers. */
internal suspend fun runFoundationDBReadAllocationProbe() {
    val allocated = allocationCounter()
    val payloadText = "x".repeat(64 * 1024)
    val store = FoundationDBDataStore.open(
        directoryPath = listOf("maryk", "read-allocation-probe", Uuid.random().toString()),
        dataModelsById = mapOf(1u to PerformanceRecord), keepAllVersions = false,
    )
    try {
        val added = store.execute(PerformanceRecord.add(*Array(32) { index -> PerformanceRecord.create {
            label with "row-$index"; payload with payloadText
        } }))
        val keys = added.statuses.map { (it as AddSuccess<PerformanceRecord>).key }.toTypedArray()
        for (projection in listOf(false, true)) {
            val times = mutableListOf<Long>()
            val allocations = mutableListOf<Long>()
            repeat(30) { round ->
                val before = allocated()
                val elapsed = measureTime {
                    val response = if (projection) store.execute(PerformanceRecord.get(*keys, select = PerformanceRecord.graph { listOf(label) }))
                        else store.execute(PerformanceRecord.get(*keys))
                    check(response.values.size == 32)
                    if (!projection) check(response.values.first().values { payload } == payloadText)
                }
                val after = allocated()
                if (round >= 10) {
                    times += elapsed.inWholeMicroseconds
                    allocations += if (before < 0 || after < 0) -1 else (after - before).coerceAtLeast(0)
                }
            }
            println("get records=32 payload=65536 projection=$projection median_us=${times.sorted()[times.size / 2]} allocated_bytes=${allocations.sorted()[allocations.size / 2]}")
        }
    } finally { store.close() }
}

/** Retains the previous algorithm as a comparison; receipt IDs in normal requests are unique. */
internal fun runFoundationDBReceiptAllocationProbe() {
    val allocated = allocationCounter()
    for (count in listOf(1_000, 4_096)) {
        val keys = List(count) { index -> ByteArray(32).also { bytes -> repeat(4) { bytes[it] = (index ushr (it * 8)).toByte() } } }
        val samples = mutableMapOf<String, MutableList<Pair<Long, Long>>>()
        repeat(5) { round ->
            for (algorithm in if (round % 2 == 0) listOf("copy-set", "queue") else listOf("queue", "copy-set")) {
                val before = allocated()
                val elapsed = measureTime {
                    if (algorithm == "copy-set") {
                        val pending = atomic<Set<Bytes>>(emptySet())
                        keys.forEach { key -> pending.update { it + Bytes(key) } }
                        check(pending.getAndSet(emptySet()).size == count)
                    } else {
                        val pending = PendingReceiptQueue(count + 1)
                        keys.forEach { check(pending.offer(it)) }
                        check(pending.drain().size == count)
                    }
                }
                val after = allocated()
                if (round > 0) samples.getOrPut(algorithm) { mutableListOf() } += elapsed.inWholeMicroseconds to
                    if (before < 0 || after < 0) -1 else (after - before).coerceAtLeast(0)
            }
        }
        samples.forEach { (name, values) -> println("receipt-cleanup keys=$count algorithm=$name median_us=${values.map { it.first }.sorted()[values.size / 2]} allocated_bytes=${values.map { it.second }.sorted()[values.size / 2]}") }
    }
}

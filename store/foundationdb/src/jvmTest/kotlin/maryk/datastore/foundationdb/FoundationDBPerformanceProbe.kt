package maryk.datastore.foundationdb

import kotlinx.coroutines.runBlocking
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.types.Version
import maryk.core.query.orders.ascending
import maryk.core.query.requests.scan
import maryk.core.properties.definitions.string
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.foundationdb.Range
import maryk.foundationdb.StreamingMode
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

internal object PerformanceRecord : RootDataModel<PerformanceRecord>() {
    val label by string(1u)
    val payload by string(2u)
}

private object PerformanceBefore : RootDataModel<PerformanceBefore>(name = "PerformanceMaintenance", version = Version(1)) {
    val label by string(1u)
    val payload by string(2u)
}

private object PerformanceAfter : RootDataModel<PerformanceAfter>(name = "PerformanceMaintenance", version = Version(1, 1),
    indexes = { listOf(PerformanceAfter.label.ref()) }) {
    val label by string(1u)
    val payload by string(2u)
}

/** Opt-in local probe. It reports distributions and verifies results; timings are never test assertions. */
fun main(args: Array<String>) = runBlocking {
    when (args.firstOrNull()) {
        "reads" -> { runFoundationDBReadAllocationProbe(); return@runBlocking }
        "receipts" -> { runFoundationDBReceiptAllocationProbe(); return@runBlocking }
        null -> Unit
        else -> error("Expected no arguments, 'reads', or 'receipts'")
    }
    val store = FoundationDBDataStore.open(
        fdbClusterFilePath = "./fdb.cluster",
        directoryPath = listOf("maryk", "performance-probe", Uuid.random().toString()),
        dataModelsById = mapOf(1u to PerformanceRecord), keepAllVersions = false,
    )
    try {
        val prefix = packKey(store.getTableDirs(1u).modelPrefix, "probe-rows".encodeToByteArray())
        store.tc.run { transaction -> repeat(3_000) { index ->
            transaction.set(prefix + "$index".padStart(8, '0').encodeToByteArray(), ByteArray(128))
        } }
        val range = Range.startsWith(prefix)
        val modes = listOf(StreamingMode.ITERATOR, StreamingMode.EXACT, StreamingMode.WANT_ALL)
        val random = Random(730)
        for (limit in listOf(1, 32, 512)) {
            val measurements = modes.associateWith { mutableListOf<Long>() }
            repeat(45) { round -> for (mode in modes.shuffled(random)) {
                val started = TimeSource.Monotonic.markNow()
                val rows = store.tc.run { it.getRange(range, limit, false, mode).asList().awaitResult() }
                check(rows.size == limit)
                if (round >= 5) measurements.getValue(mode) += started.elapsedNow().inWholeMicroseconds
            } }
            for ((mode, samples) in measurements) println("range limit=$limit mode=$mode median_us=${samples.sorted()[samples.size / 2]} p95_us=${samples.sorted()[(samples.size * 95 / 100).coerceAtMost(samples.lastIndex)]}")
        }
        val uniquePrefix = packKey(store.getTableDirs(1u).uniquePrefix, "lookup".encodeToByteArray())
        val sourceKey = packKey(store.getTableDirs(1u).modelPrefix, "old-property".encodeToByteArray())
        fun owner(index: Int) = ByteArray(24).also { bytes ->
            repeat(4) { offset -> bytes[20 + offset] = (index ushr (24 - offset * 8)).toByte() }
        }
        store.tc.run { transaction ->
            repeat(3_000) { index ->
                transaction.set(uniquePrefix + "$index".padStart(8, '0').encodeToByteArray(), owner(index))
            }
            transaction.set(sourceKey, ByteArray(8) + "2999".padStart(8, '0').encodeToByteArray())
        }
        val targetOwner = owner(2_999)
        val uniqueRange = Range.startsWith(uniquePrefix)
        val lookups = mutableMapOf("range" to mutableListOf<Long>(), "two-point" to mutableListOf<Long>())
        repeat(25) { round -> for (kind in lookups.keys.shuffled(random)) {
            val started = TimeSource.Monotonic.markNow()
            val result = store.tc.run { transaction ->
                if (kind == "two-point") {
                    val oldCell = checkNotNull(transaction.get(sourceKey).awaitResult())
                    transaction.get(uniquePrefix + oldCell.copyOfRange(8, oldCell.size)).awaitResult()
                } else {
                    val iterator = transaction.getRange(uniqueRange).iterator()
                    try {
                        var found: ByteArray? = null
                        while (iterator.hasNext()) {
                            val row = iterator.nextBlocking()
                            if (row.value.contentEquals(targetOwner)) { found = row.value; break }
                        }
                        found
                    } finally { iterator.cancel() }
                }
            }
            check(result?.contentEquals(targetOwner) == true)
            if (round >= 5) lookups.getValue(kind) += started.elapsedNow().inWholeMicroseconds
        } }
        for ((kind, samples) in lookups) println("unique-owner-lookup algorithm=$kind candidates=3000 owner_bytes=24 median_us=${samples.sorted()[samples.size / 2]}")

        val bulk = mutableMapOf(1 to mutableListOf<Long>(), 8 to mutableListOf<Long>())
        repeat(6) { round -> for (concurrency in bulk.keys.shuffled(random)) {
            val request = PerformanceRecord.add(*Array(256) { index -> PerformanceRecord.create {
                label with "batch-$round-$concurrency-$index"; payload with "p".repeat(256)
            } })
            val started = TimeSource.Monotonic.markNow()
            val response = store.bulkAdd(request, concurrency)
            check(response.statuses.size == 256 && response.statuses.all { it is AddSuccess<*> })
            if (round > 0) bulk.getValue(concurrency) += started.elapsedNow().inWholeMicroseconds
        } }
        for ((concurrency, samples) in bulk) println("bulk records=256 concurrency=$concurrency median_us=${samples.sorted()[samples.size / 2]}")
    } finally { store.close() }
    for (history in listOf(false, true)) {
        val samples = mutableMapOf(1 to mutableListOf<Long>(), 4 to mutableListOf<Long>())
        for (round in 0 until 3) for (workers in samples.keys.shuffled(Random(730 + round))) {
            val root = listOf("maryk", "maintenance-probe", Uuid.random().toString())
            val before = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = root,
                dataModelsById = mapOf(1u to PerformanceBefore), keepAllVersions = history)
            try {
                val seeded = before.bulkAdd(PerformanceBefore.add(*Array(256) { index -> PerformanceBefore.create {
                    label with "$index".padStart(5, '0'); payload with "p".repeat(16_000)
                } }))
                check(seeded.statuses.all { it is AddSuccess<*> })
            } finally { before.close() }
            val started = TimeSource.Monotonic.markNow()
            val after = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = root,
                dataModelsById = mapOf(1u to PerformanceAfter), keepAllVersions = history,
                transactionConfiguration = FoundationDBTransactionConfiguration(),
                executionConfiguration = FoundationDBExecutionConfiguration(maintenancePartitionBytes = 256 * 1024,
                    maintenanceConcurrency = workers))
            val elapsed = started.elapsedNow().inWholeMicroseconds
            try {
                val progress = checkNotNull(after.maintenanceProgress(1u))
                check(progress.completedPartitions == progress.partitions)
                val values = after.execute(PerformanceAfter.scan(order = PerformanceAfter.label.ref().ascending(), limit = 256u,
                    select = PerformanceAfter.graph { listOf(label) })).values
                check(values.map { it.values { label } } == (0 until 256).map { "$it".padStart(5, '0') })
                samples.getValue(workers) += elapsed
            } finally { after.close() }
        }
        for ((workers, times) in samples) println("maintenance records=256 payload=16000 history=$history workers=$workers median_us=${times.sorted()[times.size / 2]}")
    }
}

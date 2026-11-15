package maryk.datastore.indexeddb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import maryk.core.exceptions.RequestException
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.ValueCount
import maryk.core.aggregations.metric.ValueCountResponse
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.filters.Equals
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.datastore.indexeddb.processors.PreparedCurrentSnapshot
import maryk.datastore.indexeddb.processors.decodeCurrentSnapshotRecord
import maryk.datastore.indexeddb.processors.decodeCurrentSnapshot
import maryk.datastore.indexeddb.processors.encodeCurrentSnapshot
import maryk.datastore.shared.AtomicMutationContext
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

private object PreparedSnapshotRecord : RootDataModel<PreparedSnapshotRecord>(indexes = { listOf(PreparedSnapshotRecord.title.ref()) }) {
    val title by string(1u)
    val payload by string(2u)
}

class PreparedSnapshotReadTest {
    @Test
    fun routedMappingUsesNativePreparationAndPreservesStagedAndCustomReads() = runTest {
        installIndexedDbForTests()
        val native = openIndexedDbByteStore("routed-mapping-${Random.nextInt()}", setOf("meta", "rows"))
        try {
            val first = byteArrayOf(1)
            val second = byteArrayOf(2)
            val third = byteArrayOf(3)
            val missing = byteArrayOf(4)
            val keys = listOf(first, second, first, missing, third)
            native.put("rows", first, byteArrayOf(11))
            native.put("rows", second, byteArrayOf(12))
            var nativeMappings = 0
            val mappedStore = object : IndexedDbByteStore by native, IndexedDbBatchReadStore {
                override suspend fun getBatchValues(storeName: String, keys: List<ByteArray>): List<ByteArray?> =
                    error("Native mapping must not materialize a raw batch first")

                override suspend fun <T> mapBatchValues(storeName: String, keys: List<ByteArray>, transform: (Int, ByteArray?) -> T): List<T> {
                    nativeMappings++
                    return (native as IndexedDbBatchReadStore).mapBatchValues(storeName, keys, transform)
                }
            }
            val owner = Any()
            val routed = AtomicAwareIndexedDbByteStore(mappedStore, owner)
            val transform = { index: Int, bytes: ByteArray? -> index to bytes?.single()?.toInt() }
            val original = listOf(0 to 11, 1 to 12, 2 to 11, 3 to null, 4 to null)
            assertEquals(original, routed.mapBatchValues("rows", keys, transform))
            assertEquals(1, nativeMappings)

            val staged = StagedIndexedDbByteStore(mappedStore)
            staged.put("rows", first, byteArrayOf(22))
            staged.delete("rows", second)
            staged.put("rows", third, byteArrayOf(33))
            withContext(AtomicMutationContext(owner, staged)) {
                assertEquals(listOf(0 to 22, 1 to null, 2 to 22, 3 to null, 4 to 33),
                    routed.mapBatchValues("rows", keys, transform))
            }
            assertEquals(1, nativeMappings)

            val custom = object : IndexedDbByteStore by native {}
            assertEquals(original, AtomicAwareIndexedDbByteStore(custom, owner).mapBatchValues("rows", keys, transform))
        } finally { native.close() }
    }

    private fun snapshot(deleted: Boolean = false) = encodeCurrentSnapshot(IndexedDbRecordMeta(1uL, 2uL, deleted), listOf(
        PreparedSnapshotRecord.title.ref().toStorageByteArray() to encodeStorageValue(Value, PreparedSnapshotRecord.title.definition, "title"),
        PreparedSnapshotRecord.payload.ref().toStorageByteArray() to encodeStorageValue(Value, PreparedSnapshotRecord.payload.definition, "x".repeat(96 * 1024)),
    ))

    @Test
    fun preparationOwnsOnlySelectedRowsAndDefersValueDecoding() = runTest {
        val select = PreparedSnapshotRecord.graph { listOf(title) }
        val raw = snapshot()
        val prepared = PreparedCurrentSnapshot.prepare(PreparedSnapshotRecord, raw, select)
        assertNull(prepared.bytes)
        val selected = assertNotNull(prepared.selected)
        assertEquals(2, selected.storedRowCount)
        assertEquals(1, selected.rows.size)
        raw.fill(0)
        var decrypts = 0
        val decoded = assertNotNull(decodeCurrentSnapshotRecord(PreparedSnapshotRecord, ByteArray(16), prepared, select) { _, value ->
            decrypts++; value
        })
        assertEquals("title", decoded.values { title })
        assertNull(decoded.values { payload })
        assertEquals(1uL, decoded.firstVersion)
        assertEquals(2uL, decoded.lastVersion)
        assertEquals(1, decrypts)
    }

    @Test
    fun wideAndPayloadHeavySelectionsKeepTheOriginalBlob() = runTest {
        for (select in listOf(PreparedSnapshotRecord.graph { listOf(title, payload) },
            PreparedSnapshotRecord.graph { listOf(payload) })) {
            val raw = snapshot()
            val prepared = PreparedCurrentSnapshot.prepare(PreparedSnapshotRecord, raw, select)
            assertSame(raw, prepared.bytes)
            assertNull(prepared.selected)
            val decoded = assertNotNull(decodeCurrentSnapshotRecord(PreparedSnapshotRecord, ByteArray(16), prepared, select) { _, value -> value })
            assertEquals(96 * 1024, decoded.values { payload }?.length)
        }
    }

    @Test
    fun preparationAndOrdinaryReadsRejectMalformedRowLengths() {
        val bytes = ByteArray(96 * 1024)
        bytes[17] = 1
        bytes[18] = 0xff.toByte(); bytes[19] = 0xff.toByte(); bytes[20] = 0x7f
        assertFailsWith<IllegalArgumentException> {
            PreparedCurrentSnapshot.prepare(PreparedSnapshotRecord, bytes, PreparedSnapshotRecord.graph { listOf(title) })
        }
        assertFailsWith<IllegalArgumentException> { decodeCurrentSnapshot(bytes) }
    }

    @Test
    fun consumedSnapshotDropsRawReferencesAndRejectsReuse() = runTest {
        val select = PreparedSnapshotRecord.graph { listOf(title) }
        val prepared = PreparedCurrentSnapshot.prepare(PreparedSnapshotRecord, snapshot(), select)
        prepared.release()
        prepared.release()
        assertNull(prepared.bytes)
        assertNull(prepared.selected)
        assertFailsWith<IllegalStateException> {
            decodeCurrentSnapshotRecord(PreparedSnapshotRecord, ByteArray(16), prepared, select) { _, value -> value }
        }
    }

    @Test
    fun emptyProjectionPreservesDeletedSnapshotCompletenessAndLegacyFallback() = runTest {
        val select = PreparedSnapshotRecord.graph { emptyList() }
        val prepared = PreparedCurrentSnapshot.prepare(PreparedSnapshotRecord, snapshot(deleted = true), select)
        assertEquals(2, prepared.selected?.storedRowCount)
        assertEquals(0, prepared.selected?.rows?.size)
        val decoded = assertNotNull(decodeCurrentSnapshotRecord(PreparedSnapshotRecord, ByteArray(16), prepared, select) { _, value -> value })
        assertEquals(true, decoded.isDeleted)
        assertNull(decoded.values { payload })
        val legacy = PreparedCurrentSnapshot.prepare(PreparedSnapshotRecord, encodeRecordMeta(IndexedDbRecordMeta(1uL, 2uL, true)), select)
        assertNull(decodeCurrentSnapshotRecord(PreparedSnapshotRecord, ByteArray(16), legacy, select) { _, value -> value })
    }

    @Test
    fun largeGetAndIndexProjectionRetainOrderHiddenFieldsAndHistoricFallback() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("prepared-large-${Random.nextInt()}", mapOf(1u to PreparedSnapshotRecord), keepAllVersions = true)
        try {
            val payload = "x".repeat(96 * 1024)
            val added = store.execute(PreparedSnapshotRecord.add(*Array(6) { index -> PreparedSnapshotRecord.create {
                title with "record-$index"; this.payload with payload
            } })).statuses.map { it as AddSuccess<PreparedSnapshotRecord> }
            val keys = added.map { it.key }
            val select = PreparedSnapshotRecord.graph { listOf(title) }
            for ((count, version) in listOf(19 to null, 19 to added.maxOf { it.version }, 257 to null)) {
                val requested = Array(count) { keys[it % keys.size] }
                val result = store.execute(PreparedSnapshotRecord.get(*requested, select = select, toVersion = version,
                    where = Equals(PreparedSnapshotRecord.payload.ref() with payload),
                    aggregations = Aggregations("count" to ValueCount(PreparedSnapshotRecord.payload.ref()))))
                assertEquals(requested.toList(), result.values.map { it.key })
                assertEquals(AggregationsResponse("count" to ValueCountResponse(PreparedSnapshotRecord.payload.ref(), count.toULong())), result.aggregations)
                result.values.forEachIndexed { index, row ->
                    assertEquals("record-${index % keys.size}", row.values { title }); assertNull(row.values { this.payload })
                }
            }
            val scan = store.execute(PreparedSnapshotRecord.scan(select = select,
                order = PreparedSnapshotRecord.title.ref().ascending(), limit = 6u))
            assertEquals(keys, scan.values.map { it.key })
            scan.values.forEach { assertNull(it.values { this.payload }) }
        } finally { store.close() }
    }

    @Test
    fun staleSchemaIsRejectedBeforePreparingAnIncompatibleLargeHead() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("prepared-stale-${Random.nextInt()}", mapOf(1u to PreparedSnapshotRecord))
        try {
            val key = Key<PreparedSnapshotRecord>(ByteArray(16))
            val bad = encodeCurrentSnapshot(IndexedDbRecordMeta(1uL, 2uL, false),
                listOf(byteArrayOf(0xff.toByte(), 0x7f) to ByteArray(96 * 1024)))
            store.byteStore.put("k:1", key.bytes, bad)
            val options = assertNotNull(store.byteStore.get("meta", OptionsMetadataKey))
            store.byteStore.put("meta", OptionsMetadataKey, options + byteArrayOf(99))
            assertFailsWith<RequestException> {
                store.execute(PreparedSnapshotRecord.get(key, key, select = PreparedSnapshotRecord.graph { listOf(title) }))
            }
        } finally { store.close() }
    }

    @Test
    fun nativeMappingKeepsSchemaFirstAndPreservesMissingAndRepeatedKeys() = runTest {
        installIndexedDbForTests()
        val native = openIndexedDbByteStore("mapped-snapshot-${Random.nextInt()}", setOf("meta", "rows"))
        try {
            val schema = native.captureSchemaSnapshot(emptySet())
            native.put("rows", byteArrayOf(1), byteArrayOf(11))
            val calls = mutableListOf<Int>()
            val rows = (native as IndexedDbSchemaReadStore).mapRecordsWithSchemaRevision("rows", listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(1))) { index, value ->
                calls += index
                if (index == 0) assertContentEquals(schema.validatedReadRevision, value)
                value?.copyOf()
            }
            assertEquals(listOf(0, 1, 2, 3), calls)
            assertNull(rows[2])
            rows[1]!![0] = 99
            assertContentEquals(byteArrayOf(11), rows[3])
            assertContentEquals(byteArrayOf(11), native.get("rows", byteArrayOf(1)))
        } finally { native.close() }
    }

    @Test
    fun failedNativeMappingKeepsItsCauseAndStopsPreparingRemainingRows() = runTest {
        installIndexedDbForTests()
        val native = openIndexedDbByteStore("mapped-failure-${Random.nextInt()}", setOf("meta", "rows"))
        try {
            val failure = IllegalStateException("projection failed")
            val calls = mutableListOf<Int>()
            assertSame(failure, assertFailsWith<IllegalStateException> {
                (native as IndexedDbSchemaReadStore).mapRecordsWithSchemaRevision("rows", List(4) { byteArrayOf(it.toByte()) }) { index, value ->
                    calls += index
                    if (index == 2) throw failure
                    value
                }
            })
            assertEquals(listOf(0, 1, 2), calls)
            native.put("rows", byteArrayOf(1), byteArrayOf(11))
            assertContentEquals(byteArrayOf(11), native.get("rows", byteArrayOf(1)))
        } finally { native.close() }
    }

    @Test
    fun cancellationDuringNativeMappingStopsRemainingPreparation() = runTest {
        installIndexedDbForTests()
        val native = openIndexedDbByteStore("mapped-cancel-${Random.nextInt()}", setOf("meta", "rows"))
        try {
            val reached = CompletableDeferred<Unit>()
            val calls = mutableListOf<Int>()
            lateinit var job: Job
            job = launch {
                (native as IndexedDbSchemaReadStore).mapRecordsWithSchemaRevision("rows", List(4) { byteArrayOf(it.toByte()) }) { index, value ->
                    calls += index
                    if (index == 1) { job.cancel(CancellationException("stop projection")); reached.complete(Unit) }
                    value
                }
            }
            reached.await()
            job.cancelAndJoin()
            native.put("rows", byteArrayOf(1), byteArrayOf(11))
            assertEquals(listOf(0, 1), calls)
        } finally { native.close() }
    }
}

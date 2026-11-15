package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.indexeddb.processors.collectCurrentUniqueRows
import maryk.datastore.indexeddb.processors.createStoragePlan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

private object BatchedUniqueMap : RootDataModel<BatchedUniqueMap>() {
    val entries by map(1u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition(unique = true))
}

class UniqueBatchReadTest {
    @Test
    fun changingAndClearingUniqueValuesNeverDeletesAnotherOwnersClaim() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("unique-diff-owner-${Random.nextInt()}", mapOf(1u to BatchedUniqueMap))
        val first = Key<BatchedUniqueMap>(ByteArray(16) { 1 })
        val second = Key<BatchedUniqueMap>(ByteArray(16) { 2 })
        val fields = IndexedDbSensitiveFieldSupport(mapOf(1u to BatchedUniqueMap), null)
        fun values(value: String) = BatchedUniqueMap.create { entries with mapOf("key" to value) }
        try {
            assertIs<AddSuccess<BatchedUniqueMap>>(store.execute(BatchedUniqueMap.add(first to values("old"))).statuses.single())
            assertIs<AddSuccess<BatchedUniqueMap>>(store.execute(BatchedUniqueMap.add(second to values("other"))).statuses.single())
            val oldKey = createStoragePlan(BatchedUniqueMap, 1u, first.bytes, values("old"), fields).uniqueRows.single().uniqueKey
            store.byteStore.put("u:1", oldKey, second.bytes)
            assertIs<ChangeSuccess<BatchedUniqueMap>>(store.execute(BatchedUniqueMap.change(first.change(
                Change(BatchedUniqueMap.entries.ref() with mapOf("key" to "new"))
            ))).statuses.single())
            val newKey = createStoragePlan(BatchedUniqueMap, 1u, first.bytes, values("new"), fields).uniqueRows.single().uniqueKey
            assertContentEquals(second.bytes, store.byteStore.get("u:1", oldKey))
            assertContentEquals(first.bytes, store.byteStore.get("u:1", newKey))
            assertIs<ChangeSuccess<BatchedUniqueMap>>(store.execute(BatchedUniqueMap.change(first.change(
                Change(BatchedUniqueMap.entries.ref() with emptyMap())
            ))).statuses.single())
            assertNull(store.byteStore.get("u:1", newKey))
            assertContentEquals(second.bytes, store.byteStore.get("u:1", oldKey))
        } finally { store.close() }
    }

    @Test
    fun addsRejectEveryExistingOwnerIncludingAnOrphanedSelfClaim() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("unique-add-${Random.nextInt()}", mapOf(1u to BatchedUniqueMap))
        try {
            val values = BatchedUniqueMap.create { entries with (0 until 300).associate { "key-$it" to "value-$it" } }
            assertIs<AddSuccess<BatchedUniqueMap>>(store.execute(BatchedUniqueMap.add(values)).statuses.single())
            assertIs<ValidationFail<BatchedUniqueMap>>(store.execute(BatchedUniqueMap.add(values)).statuses.single())

            val orphanKey = ByteArray(BatchedUniqueMap.Meta.keyByteSize) { 9 }
            val orphanValues = BatchedUniqueMap.create { entries with mapOf("orphan" to "claimed") }
            val plan = createStoragePlan(BatchedUniqueMap, 1u, orphanKey, orphanValues,
                IndexedDbSensitiveFieldSupport(mapOf(1u to BatchedUniqueMap), null))
            store.byteStore.put("u:1", plan.uniqueRows.single().uniqueKey, orphanKey)
            assertIs<ValidationFail<BatchedUniqueMap>>(store.execute(BatchedUniqueMap.add(Key<BatchedUniqueMap>(orphanKey) to orphanValues)).statuses.single())
        } finally { store.close() }
    }

    @Test
    fun uniqueOwnershipUsesBoundedBatchesAndKeepsOnlyRowsOwnedByThisRecord() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("unique-batch-${Random.nextInt()}", mapOf(1u to BatchedUniqueMap))
        try {
            val key = ByteArray(BatchedUniqueMap.Meta.keyByteSize) { 1 }
            val otherKey = ByteArray(BatchedUniqueMap.Meta.keyByteSize) { 2 }
            val values = BatchedUniqueMap.create { entries with (0 until 300).associate { "key-$it" to "value-$it" } }
            val plan = createStoragePlan(BatchedUniqueMap, 1u, key, values,
                IndexedDbSensitiveFieldSupport(mapOf(1u to BatchedUniqueMap), null))
            assertEquals(300, plan.uniqueRows.size)
            val owners = plan.uniqueRows.mapIndexed { index, row ->
                Bytes(row.uniqueKey) to when (index % 3) { 0 -> key; 1 -> otherKey; else -> null }
            }.toMap()
            val batchSizes = mutableListOf<Int>()
            val native = object : IndexedDbByteStore by store.byteStore, IndexedDbBatchReadStore {
                override suspend fun get(storeName: String, key: ByteArray): ByteArray? = error("Unique ownership must use batches")
                override suspend fun getBatchValues(storeName: String, keys: List<ByteArray>): List<ByteArray?> {
                    batchSizes += keys.size
                    return keys.map { owners[Bytes(it)] }
                }
            }
            val rows = store.collectCurrentUniqueRows(BatchedUniqueMap, 1u, "t:1", "u:1", key,
                uniqueStore = native, tableRows = plan.tableRows)
            assertEquals(listOf(256, 44), batchSizes)
            assertEquals(100, rows.count { it.existingKeys.isNotEmpty() })
            assertEquals(plan.uniqueRows.map { Bytes(it.uniqueKey) }, rows.map { Bytes(it.uniqueKey) })
        } finally { store.close() }
    }
}

package maryk.datastore.foundationdb

import kotlinx.coroutines.CompletableDeferred
import kotlin.test.assertIs
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.Cache
import maryk.datastore.foundationdb.processors.processGetRequest
import maryk.core.exceptions.StorageException
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.requests.change
import maryk.core.query.responses.TransactionStatus
import maryk.datastore.shared.beginTransaction
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.properties.types.Key
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.orders.Order
import maryk.core.query.requests.delete
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.foundationdb.model.validateModelSchemaReady
import maryk.datastore.foundationdb.model.matchesReadySchemaFence
import maryk.datastore.foundationdb.processors.helpers.filterReadQualifiers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.uuid.Uuid

private object FilterPreparationModel : RootDataModel<FilterPreparationModel>() {
    val first by string(1u)
    val second by string(2u)
    val third by string(3u)
    val entries by map(4u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

class ReadPreparationTest {
    @Test
    fun tableFilterWindowsPreserveOrderLimitsDeletionAndHistory() = runBoundedIntegrationTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "table-filter-window", Uuid.random().toString()),
            dataModelsById = mapOf(1u to FilterPreparationModel), keepAllVersions = true,
        )
        try {
            val keys = List(33) { index -> Key<FilterPreparationModel>(ByteArray(16).also { it[15] = index.toByte() }) }
            val added = store.execute(FilterPreparationModel.add(*keys.mapIndexed { index, key ->
                key to FilterPreparationModel.create {
                    first with "first-$index"; second with if (index % 3 == 2) "match" else "other"; third with "third"
                    entries with mapOf("key" to "entry-$index")
                }
            }.toTypedArray())).statuses.map { assertIs<AddSuccess<FilterPreparationModel>>(it) }
            val deleted = setOf(2, 8, 17, 26)
            store.execute(FilterPreparationModel.delete(*deleted.map { keys[it] }.toTypedArray()))
            val select = FilterPreparationModel.graph { listOf(first) }
            for (version in listOf(null, added.maxOf { it.version })) for (descending in listOf(false, true))
                for (includeDeleted in listOf(false, true)) for (limit in listOf(1u, 3u, 10u)) for (start in listOf(null, 16)) {
                val expected = (0..32).filter { index ->
                    index % 3 == 2 && (version != null || includeDeleted || index !in deleted) &&
                        (start == null || if (descending) index < start else index > start)
                }.let { if (descending) it.reversed() else it }.take(limit.toInt())
                val rows = store.execute(FilterPreparationModel.scan(
                    startKey = start?.let { keys[it] }, includeStart = false, select = select,
                    where = Equals(FilterPreparationModel.second.ref() with "match"),
                    order = if (descending) Order.descending else Order.ascending,
                    limit = limit, filterSoftDeleted = !includeDeleted, toVersion = version, allowTableScan = true,
                )).values
                assertEquals(expected.map { keys[it] }, rows.map { it.key })
                rows.forEach { row ->
                    assertEquals("first-${keys.indexOf(row.key)}", row.values { first })
                    assertNull(row.values { second })
                    assertNull(row.values { entries })
                }
            }
        } finally { store.close() }
    }

    @Test
    fun projectedBatchesPreservePinnedSnapshotAcrossNativeTransactions() = runBoundedIntegrationTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "projected-batch-pinned", Uuid.random().toString()),
            dataModelsById = mapOf(1u to FilterPreparationModel), keepAllVersions = true,
        )
        try {
            val keys = store.execute(FilterPreparationModel.add(*Array(2) { index ->
                FilterPreparationModel.create {
                    first with "before-$index"; second with "second"; third with "third"
                    entries with mapOf("key" to "entry")
                }
            })).statuses.map { (it as AddSuccess<FilterPreparationModel>).key }
            val select = FilterPreparationModel.graph { listOf(first, second) }
            val context = store.createReadContext()
            suspend fun pinnedBatch(): List<String?> {
                val request = FilterPreparationModel.get(*keys.toTypedArray(), select = select)
                val response = CompletableDeferred<ValuesResponse<FilterPreparationModel>>()
                store.processGetRequest(StoreAction(request, response), Cache.forRequest(request), context)
                val result = response.await()
                result.values.forEach { assertNull(it.values { entries }) }
                return result.values.map { it.values { first } }
            }
            assertEquals(listOf("before-0", "before-1"), pinnedBatch())
            store.execute(FilterPreparationModel.change(keys[0].change(Change(FilterPreparationModel.ref { first } with "after"))))
            assertEquals(listOf("before-0", "before-1"), pinnedBatch())
            assertEquals("after", store.execute(FilterPreparationModel.get(keys[0])).values.single().values { first })
        } finally { store.close() }
    }

    @Test
    fun projectedBatchesSeeStagedWritesInExplicitTransactions() = runBoundedIntegrationTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "projected-batch-ryw", Uuid.random().toString()),
            dataModelsById = mapOf(1u to FilterPreparationModel), keepAllVersions = true,
        )
        try {
            val keys = store.execute(FilterPreparationModel.add(*Array(2) { index ->
                FilterPreparationModel.create {
                    first with "base-$index"; second with "second"; third with "third"
                    entries with mapOf("key" to "entry")
                }
            })).statuses.map { (it as AddSuccess<FilterPreparationModel>).key }
            val select = FilterPreparationModel.graph { listOf(first, second) }
            val tx = store.beginTransaction()
            tx.execute(FilterPreparationModel.change(keys[0].change(Change(FilterPreparationModel.ref { first } with "staged"))))
            val preview = tx.execute(FilterPreparationModel.get(*keys.toTypedArray(), select = select)).response
            assertEquals(listOf("staged", "base-1"), preview.values.map { it.values { first } })
            assertEquals("base-0", store.execute(FilterPreparationModel.get(keys[0])).values.single().values { first })
            assertEquals(TransactionStatus.Committed, tx.commit().status)
            val committed = store.execute(FilterPreparationModel.get(*keys.toTypedArray(), select = select))
            assertEquals(listOf("staged", "base-1"), committed.values.map { it.values { first } })
            committed.values.forEach { assertNull(it.values { entries }) }
        } finally { store.close() }
    }

    @Test
    fun batchedReadsHandleMissingKeysAndUnmemoizedDuplicates() = runBoundedIntegrationTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "prepared-first-window", Uuid.random().toString()),
            dataModelsById = mapOf(1u to FilterPreparationModel), keepAllVersions = true,
        )
        try {
            val statuses = store.execute(FilterPreparationModel.add(*Array(2) { index ->
                FilterPreparationModel.create {
                    first with "record-$index"; second with "second"; third with "third"
                    entries with mapOf("key" to "entry")
                }
            })).statuses.map { it as AddSuccess<FilterPreparationModel> }
            val keys = statuses.map { it.key }
            val missing = Key<FilterPreparationModel>(ByteArray(keys.first().size) { 0x7f })
            val selection = FilterPreparationModel.graph { listOf(first, second) }
            val requested = listOf(missing, keys[0], keys[0], missing, keys[1], keys[0], missing, keys[1])
            val selected = store.execute(FilterPreparationModel.get(*requested.toTypedArray(), select = selection))
            assertEquals(requested.filterNot { it == missing }, selected.values.map { it.key })
            selected.values.forEach { assertNull(it.values { entries }) }
            // Above the memoization planning bound, every duplicate still owns a fresh read.
            val large = Array(4097) { keys[it % keys.size] }
            val result = store.execute(FilterPreparationModel.get(*large, select = selection))
            assertEquals(large.toList(), result.values.map { it.key })
            result.values.forEachIndexed { index, record ->
                assertEquals("record-${index % keys.size}", record.values { first })
            }
        } finally { store.close() }
    }

    @Test
    fun singleProjectionsPreserveScalarLayoutsAndDeletedRecords() = runBoundedIntegrationTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "prepared-single-projections", Uuid.random().toString()),
            dataModelsById = mapOf(1u to FilterPreparationModel), keepAllVersions = true,
        )
        try {
            val added = store.execute(FilterPreparationModel.add(FilterPreparationModel.create {
                first with "first"; second with "second"; third with "third"
                entries with mapOf("key" to "entry")
            })).statuses.single() as AddSuccess<FilterPreparationModel>
            val selections = listOf(
                FilterPreparationModel.graph { emptyList() },
                FilterPreparationModel.graph { listOf(first) },
                FilterPreparationModel.graph { listOf(second) },
                FilterPreparationModel.graph { listOf(first, third) },
                FilterPreparationModel.graph { listOf(first, second, third) },
                FilterPreparationModel.graph { listOf(entries) },
            )
            val missing = Key<FilterPreparationModel>(ByteArray(added.key.size) { 0x7f })
            for (selection in selections) {
                for (version in listOf(null, added.version)) {
                    val expected = store.execute(FilterPreparationModel.get(added.key, toVersion = version)).values.single()
                    val actual = store.execute(FilterPreparationModel.get(added.key, select = selection,
                        toVersion = version, where = Equals(FilterPreparationModel.third.ref() with "third"))).values.single()
                    if (selection.contains(1u)) assertEquals(expected.values { first }, actual.values { first })
                    if (selection.contains(2u)) assertEquals(expected.values { second }, actual.values { second })
                    if (selection.contains(3u)) assertEquals(expected.values { third }, actual.values { third })
                    if (selection.contains(4u)) assertEquals(expected.values { entries }, actual.values { entries })
                    assertEquals(expected.lastVersion, actual.lastVersion)
                }
                assertTrue(store.execute(FilterPreparationModel.get(missing, select = selection)).values.isEmpty())
            }
            store.execute(FilterPreparationModel.delete(added.key))
            for (selection in selections) {
                assertTrue(store.execute(FilterPreparationModel.get(added.key, select = selection)).values.isEmpty())
                val deleted = store.execute(FilterPreparationModel.get(added.key, select = selection,
                    filterSoftDeleted = false)).values.single()
                assertTrue(deleted.isDeleted)
                assertFalse(store.execute(FilterPreparationModel.get(added.key, select = selection,
                    toVersion = added.version)).values.single().isDeleted)
            }
        } finally { store.close() }
    }

    @Test
    fun fullSingleReadPreservesFiltersDeletionAndHistoricFallback() = runBoundedIntegrationTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "prepared-full-single", Uuid.random().toString()),
            dataModelsById = mapOf(1u to FilterPreparationModel), keepAllVersions = true,
        )
        try {
            val added = store.execute(FilterPreparationModel.add(FilterPreparationModel.create {
                first with "first"; second with "second"; third with "third"
                entries with mapOf("key" to "entry")
            })).statuses.single() as AddSuccess<FilterPreparationModel>
            val found = store.execute(FilterPreparationModel.get(added.key,
                where = Equals(FilterPreparationModel.third.ref() with "third"))).values.single()
            assertEquals(mapOf("key" to "entry"), found.values { entries })
            assertEquals(added.version, found.lastVersion)
            assertTrue(store.execute(FilterPreparationModel.get(added.key,
                where = Equals(FilterPreparationModel.third.ref() with "different"))).values.isEmpty())
            val missing = Key<FilterPreparationModel>(ByteArray(added.key.size) { 0x7f })
            assertTrue(store.execute(FilterPreparationModel.get(missing)).values.isEmpty())
            store.execute(FilterPreparationModel.delete(added.key))
            assertTrue(store.execute(FilterPreparationModel.get(added.key)).values.isEmpty())
            val deleted = store.execute(FilterPreparationModel.get(added.key, filterSoftDeleted = false)).values.single()
            assertTrue(deleted.isDeleted)
            assertEquals(mapOf("key" to "entry"), deleted.values { entries })
            val historic = store.execute(FilterPreparationModel.get(added.key, toVersion = added.version)).values.single()
            assertFalse(historic.isDeleted)
            assertEquals("third", historic.values { third })
        } finally { store.close() }
    }

    @Test
    fun batchSelectionsPreserveRecordOrderDuplicatesAndHistoricFields() = runBoundedIntegrationTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "prepared-selection-batch", Uuid.random().toString()),
            dataModelsById = mapOf(1u to FilterPreparationModel),
            keepAllVersions = true,
        )
        try {
            val statuses = store.execute(FilterPreparationModel.add(*Array(12) { index ->
                FilterPreparationModel.create {
                    first with "first-$index"
                    second with "second-$index"
                    third with "third-$index"
                    entries with mapOf("key" to "entry-$index")
                }
            })).statuses.map { it as AddSuccess<FilterPreparationModel> }
            val keys = statuses.map { it.key }
            val missing = Key<FilterPreparationModel>(ByteArray(keys.first().size) { 0x7f })
            val requested = keys.take(8) + listOf(keys.first(), missing) + keys.drop(8)
            val expected = requested.filterNot { it == missing }
            val leading = FilterPreparationModel.graph { listOf(first, second) }
            val gapped = FilterPreparationModel.graph { listOf(first, third) }
            for (version in listOf(null, statuses.maxOf { it.version })) {
                for (selection in listOf(leading, gapped)) {
                    val rows = store.execute(FilterPreparationModel.get(*requested.toTypedArray(),
                        select = selection, toVersion = version)).values
                    assertEquals(expected, rows.map { it.key })
                    for (row in rows) {
                        val index = keys.indexOf(row.key)
                        assertEquals("first-$index", row.values { first })
                        if (selection === leading) {
                            assertEquals("second-$index", row.values { second })
                            assertNull(row.values { third })
                        } else {
                            assertEquals("third-$index", row.values { third })
                            assertNull(row.values { second })
                        }
                        assertNull(row.values { entries })
                    }
                }
            }
            val collections = FilterPreparationModel.graph { listOf(entries) }
            val rows = store.execute(FilterPreparationModel.get(*requested.toTypedArray(), select = collections)).values
            assertEquals(expected, rows.map { it.key })
            for (row in rows) {
                assertEquals(mapOf("key" to "entry-${keys.indexOf(row.key)}"), row.values { entries })
                assertNull(row.values { first })
            }
        } finally { store.close() }
    }

    @Test
    fun preparedCurrentDeleteChecksPreserveFiltersAndHistoryAcrossBatches() = runBoundedIntegrationTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "prepared-delete-batch", Uuid.random().toString()),
            dataModelsById = mapOf(1u to FilterPreparationModel), keepAllVersions = true,
        )
        try {
            val added = store.execute(FilterPreparationModel.add(*Array(12) { index ->
                FilterPreparationModel.create {
                    first with "first-$index"; second with "second-$index"; third with "third-$index"
                    entries with mapOf("key" to "entry-$index")
                }
            })).statuses.map { it as AddSuccess<FilterPreparationModel> }
            val keys = added.map { it.key }
            store.execute(FilterPreparationModel.delete(keys[1], keys[9]))
            val requested = keys + listOf(keys[1], keys[11])
            val projection = FilterPreparationModel.graph { listOf(first, second) }
            val current = store.execute(FilterPreparationModel.get(*requested.toTypedArray(), select = projection)).values
            assertEquals(requested.filterNot { it == keys[1] || it == keys[9] }, current.map { it.key })
            val includingDeleted = store.execute(FilterPreparationModel.get(*requested.toTypedArray(), select = projection,
                filterSoftDeleted = false)).values
            assertEquals(requested, includingDeleted.map { it.key })
            assertEquals(listOf(keys[1], keys[9], keys[1]), includingDeleted.filter { it.isDeleted }.map { it.key })
            val filtered = store.execute(FilterPreparationModel.get(*requested.toTypedArray(), select = projection,
                where = Equals(FilterPreparationModel.third.ref() with "third-11"))).values
            assertEquals(listOf(keys[11], keys[11]), filtered.map { it.key })
            val historic = store.execute(FilterPreparationModel.get(*requested.toTypedArray(), select = projection,
                toVersion = added.maxOf { it.version })).values
            assertEquals(requested, historic.map { it.key })
            assertTrue(historic.none { it.isDeleted })
            assertTrue(store.execute(FilterPreparationModel.get(keys[1], select = projection)).values.isEmpty())
        } finally { store.close() }
    }

    @Test
    fun schemaValidationRetainsFallbackAndRejectionRules() {
        val epoch = ByteArray(16) { it.toByte() }
        val ready = "v=1\nstate=ready\nepoch=000102030405060708090a0b0c0d0e0f\n".encodeToByteArray()
        validateModelSchemaReady(ready, epoch)
        validateModelSchemaReady("epoch=000102030405060708090A0B0C0D0E0F\nstate=ready\nv=1\n".encodeToByteArray(), epoch)
        validateModelSchemaReady(null, null)
        assertFailsWith<StorageException> { validateModelSchemaReady(null, epoch) }
        assertFailsWith<StorageException> { validateModelSchemaReady(ready, null) }
        assertFailsWith<StorageException> { validateModelSchemaReady(ready, epoch.copyOf().also { it[0] = 1 }) }
        assertFailsWith<StorageException> { validateModelSchemaReady("v=2\nstate=ready\nepoch=00\n".encodeToByteArray(), epoch) }
        assertFailsWith<StorageException> { validateModelSchemaReady("v=1\nstate=rebuilding\nprevious=\nnext=00\ntransition=t\nowner=o\ntarget=m\n".encodeToByteArray(), epoch) }
    }

    @Test
    fun readyFenceFastPathRequiresEveryCanonicalByteAndEpoch() {
        val epoch = ByteArray(16) { (it * 17).toByte() }
        val canonical = "v=1\nstate=ready\nepoch=00112233445566778899aabbccddeeff\n".encodeToByteArray()
        assertTrue(matchesReadySchemaFence(canonical, epoch))
        for (index in canonical.indices) {
            val changed = canonical.copyOf()
            changed[index] = (changed[index].toInt() xor 1).toByte()
            assertFalse(matchesReadySchemaFence(changed, epoch), "Changed byte $index")
        }
        assertFalse(matchesReadySchemaFence(canonical, epoch.copyOf().also { it[0] = 1 }))
        assertFalse(matchesReadySchemaFence(canonical + byteArrayOf(10), epoch))
        assertFalse(matchesReadySchemaFence(canonical.copyOf(canonical.size - 1), epoch))
        assertFalse(matchesReadySchemaFence("v=1\nstate=rebuilding\n".encodeToByteArray(), epoch))
        assertFalse(matchesReadySchemaFence("v=1\nstate=ready\nepoch=00112233445566778899AABBCCDDEEFF\n".encodeToByteArray(), epoch))
    }

    @Test
    fun filterReadAheadDeduplicatesAndBoundsScalarQualifiers() {
        val qualifiers = filterReadQualifiers(And(
            Equals(FilterPreparationModel.first.ref() with "one"),
            Equals(FilterPreparationModel.first.ref() with "one"),
            Equals(FilterPreparationModel.second.ref() with "two"),
            Equals(FilterPreparationModel.third.ref() with "three"),
        ), true)
        assertEquals(3, qualifiers.size)
        assertTrue(qualifiers[0].contentEquals(byteArrayOf(0)))
        assertTrue(qualifiers[1].contentEquals(FilterPreparationModel.first.ref().toStorageByteArray()))
        assertTrue(qualifiers[2].contentEquals(FilterPreparationModel.second.ref().toStorageByteArray()))
        assertEquals(0, filterReadQualifiers(null, false).size)
        assertEquals(1, filterReadQualifiers(null, true).size)
    }
    @Test
    fun singleHistoricalScalarProjectionsKeepCreationAndDeletionBoundaries() = runBoundedIntegrationTest {
        val model = FilterPreparationModel
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "single-historic-preparation", Uuid.random().toString()),
            dataModelsById = mapOf(1u to model), keepAllVersions = true,
        )
        try {
            val created = assertIs<AddSuccess<FilterPreparationModel>>(store.execute(model.add(model.create {
                first with "visible"; second with "original"; third with "third"; entries with mapOf("child" to "hidden")
            })).statuses.single())
            val changed = assertIs<ChangeSuccess<FilterPreparationModel>>(store.execute(model.change(created.key.change(
                Change(model.second.ref() with "changed"),
            ))).statuses.single())
            val deleted = assertIs<DeleteSuccess<FilterPreparationModel>>(store.execute(model.delete(created.key)).statuses.single())
            val restored = assertIs<ChangeSuccess<FilterPreparationModel>>(store.execute(model.change(created.key.change(
                ObjectSoftDeleteChange(false),
            ))).statuses.single())
            val select = model.graph { listOf(first) }
            for (version in listOf(created.version, changed.version, deleted.version, restored.version)) {
                val row = store.execute(model.get(created.key, select = select, toVersion = version, filterSoftDeleted = false)).values.single()
                assertEquals("visible", row.values { first })
                assertNull(row.values { second })
                assertNull(row.values { entries })
                assertEquals(created.version, row.firstVersion)
                assertEquals(when (version) {
                    deleted.version -> deleted.version
                    restored.version -> restored.version
                    else -> created.version
                }, row.lastVersion)
                assertEquals(version == deleted.version, row.isDeleted)
            }
            assertTrue(store.execute(model.get(created.key, select = select, toVersion = deleted.version)).values.isEmpty())
            assertTrue(store.execute(model.get(created.key, select = select, toVersion = created.version - 1uL)).values.isEmpty())
            val missing = Key<FilterPreparationModel>(ByteArray(model.Meta.keyByteSize) { 127 })
            assertTrue(store.execute(model.get(missing, select = select, toVersion = created.version)).values.isEmpty())
            val late = assertIs<AddSuccess<FilterPreparationModel>>(store.execute(model.add(model.create {
                first with "later"; second with "later"; third with "later"; entries with emptyMap()
            })).statuses.single())
            assertTrue(store.execute(model.get(late.key, select = select, toVersion = created.version)).values.isEmpty())
            assertTrue(store.execute(model.get(created.key, select = select, toVersion = changed.version,
                where = Equals(model.second.ref() with "original"))).values.isEmpty())
            assertEquals("visible", store.execute(model.get(created.key, select = select, toVersion = created.version,
                where = Equals(model.second.ref() with "original"))).values.single().values { first })
        } finally { store.close() }
    }


}

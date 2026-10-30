package maryk.datastore.foundationdb

import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.query.filters.Equals
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private object DeletedInclusiveScanRecord : RootDataModel<DeletedInclusiveScanRecord>(indexes = { listOf(DeletedInclusiveScanRecord.indexed.ref()) }) {
    val unique by string(1u, unique = true)
    val indexed by string(2u)
}

class DeletedInclusiveScanTest {
    @Test
    fun uniqueAndIndexedFiltersIncludeEveryDeletedOwner() = runTest {
        for (history in listOf(false, true)) {
            val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "deleted-inclusive-scan-$history-${Random.nextInt()}"),
                dataModelsById = mapOf(1u to DeletedInclusiveScanRecord), keepAllVersions = history)
            try { checkDeletedInclusiveScans(store, history) } finally { store.close() }
        }
    }
}

private suspend fun checkDeletedInclusiveScans(store: IsDataStore, history: Boolean) {
    suspend fun add() = assertIs<AddSuccess<DeletedInclusiveScanRecord>>(store.execute(DeletedInclusiveScanRecord.add(
        DeletedInclusiveScanRecord.create { unique with "shared"; indexed with "group" }
    )).statuses.single())
    val first = add()
    store.execute(DeletedInclusiveScanRecord.delete(first.key))
    val second = add()
    val removed = assertIs<DeleteSuccess<DeletedInclusiveScanRecord>>(store.execute(DeletedInclusiveScanRecord.delete(second.key)).statuses.single())
    val active = add()
    val expected = setOf(first.key, second.key, active.key)
    assertEquals(expected, store.execute(DeletedInclusiveScanRecord.scan(
        where = Equals(DeletedInclusiveScanRecord.ref { unique } with "shared"),
        filterSoftDeleted = false, allowTableScan = true
    )).values.map { it.key }.toSet())
    assertEquals(expected, store.execute(DeletedInclusiveScanRecord.scan(
        where = Equals(DeletedInclusiveScanRecord.ref { indexed } with "group"),
        filterSoftDeleted = false, allowTableScan = true
    )).values.map { it.key }.toSet())
    assertEquals(listOf(active.key), store.execute(DeletedInclusiveScanRecord.scan(
        where = Equals(DeletedInclusiveScanRecord.ref { unique } with "shared")
    )).values.map { it.key })
    if (history) {
        assertEquals(setOf(first.key, second.key), store.execute(DeletedInclusiveScanRecord.scan(
            where = Equals(DeletedInclusiveScanRecord.ref { unique } with "shared"),
            toVersion = removed.version, filterSoftDeleted = false, allowTableScan = true
        )).values.map { it.key }.toSet())
        assertEquals(setOf(first.key, second.key), store.execute(DeletedInclusiveScanRecord.scan(
            where = Equals(DeletedInclusiveScanRecord.ref { indexed } with "group"),
            toVersion = removed.version, filterSoftDeleted = false, allowTableScan = true
        )).values.map { it.key }.toSet())
    }
    assertFailsWith<StorageException> {
        store.execute(DeletedInclusiveScanRecord.scan(
            where = Equals(DeletedInclusiveScanRecord.ref { unique } with "shared"), filterSoftDeleted = false
        ))
    }
    assertFailsWith<RequestException> {
        store.execute(DeletedInclusiveScanRecord.scan(
            order = DeletedInclusiveScanRecord.ref { indexed }.ascending(),
            filterSoftDeleted = false, allowTableScan = true
        ))
    }
}

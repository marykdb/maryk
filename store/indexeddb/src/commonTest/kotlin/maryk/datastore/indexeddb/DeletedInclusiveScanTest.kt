package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.filters.Equals
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.ScanCursor
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.indexeddb.processors.decodeCurrentSnapshot
import maryk.datastore.indexeddb.processors.encodeCurrentSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.random.Random

private object DeletedInclusiveModel : RootDataModel<DeletedInclusiveModel>(indexes = { listOf(DeletedInclusiveModel.group.ref()) }) {
    val email by string(1u, unique = true)
    val group by string(2u)
}

class DeletedInclusiveScanTest {
    @Test
    fun releasedEmptyDeletedSnapshotRecoversRetainedValuesAfterReopen() = runTest {
        installIndexedDbForTests()
        val database = "maryk-deleted-snapshot-${Random.nextInt()}"
        suspend fun open() = IndexedDbDataStore.open(database, mapOf(1u to DeletedInclusiveModel))
        var store = open()
        try {
            val values = DeletedInclusiveModel.create { email with "retained@example.com"; group with "retained" }
            val added = assertIs<AddSuccess<DeletedInclusiveModel>>(store.execute(DeletedInclusiveModel.add(values)).statuses.single())
            store.execute(DeletedInclusiveModel.delete(added.key))
            val (meta, rows) = decodeCurrentSnapshot(assertNotNull(store.byteStore.get("k:1", added.key.bytes)))
            assertEquals(false, rows.isEmpty())
            store.byteStore.writeBatch(listOf(IndexedDbWriteOperation.Put("k:1", added.key.bytes,
                encodeCurrentSnapshot(meta, emptyList()))))
            store.close()
            store = open()
            assertEquals(values, store.execute(DeletedInclusiveModel.get(added.key, filterSoftDeleted = false)).values.single().values)
            val filtered = store.execute(DeletedInclusiveModel.scan(
                where = Equals(DeletedInclusiveModel.ref { group } with "retained"),
                filterSoftDeleted = false, allowTableScan = true,
            ))
            assertEquals(listOf(added.key), filtered.values.map { it.key })
            assertIs<ChangeSuccess<DeletedInclusiveModel>>(store.execute(DeletedInclusiveModel.change(
                added.key.change(ObjectSoftDeleteChange(false)),
            )).statuses.single())
            assertEquals(values, store.execute(DeletedInclusiveModel.get(added.key)).values.single().values)
        } finally { store.close() }
    }

    @Test
    fun tableScanIncludesEveryDeletedDuplicateAndPreservesCursorOrder() = runTest {
        installIndexedDbForTests()
        for (keepHistory in listOf(false, true)) {
            val store = IndexedDbDataStore.open("maryk-deleted-inclusive-${Random.nextInt()}", mapOf(1u to DeletedInclusiveModel), keepAllVersions = keepHistory)
            try {
                val keys = (1..3).map { n -> Key<DeletedInclusiveModel>(ByteArray(DeletedInclusiveModel.Meta.keyByteSize) { if (it == 15) n.toByte() else 0 }) }
                var latestVersion = 0uL
                for ((index, key) in keys.withIndex()) {
                    val values = DeletedInclusiveModel.create { email with "shared@example.com"; group with "shared" }
                    latestVersion = assertIs<AddSuccess<DeletedInclusiveModel>>(store.execute(DeletedInclusiveModel.add(key to values)).statuses.single()).version
                    if (index < 2) store.execute(DeletedInclusiveModel.delete(key))
                }
                val filters = listOf(
                    Equals(DeletedInclusiveModel.ref { email } with "shared@example.com"),
                    Equals(DeletedInclusiveModel.ref { group } with "shared"),
                )
                for (filter in filters) {
                    val pagedKeys = mutableListOf<Key<DeletedInclusiveModel>>()
                    var cursor: ScanCursor? = null
                    do {
                        val page = store.execute(DeletedInclusiveModel.scan(where = filter, filterSoftDeleted = false,
                            allowTableScan = true, limit = 1u, cursor = cursor))
                        pagedKeys += page.values.map { it.key }
                        cursor = page.nextCursor
                    } while (cursor != null)
                    assertEquals(keys, pagedKeys)
                    assertEquals(listOf(keys.last()), store.execute(DeletedInclusiveModel.scan(where = filter)).values.map { it.key })
                    if (keepHistory) {
                        assertEquals(keys, store.execute(DeletedInclusiveModel.scan(where = filter, filterSoftDeleted = false,
                            allowTableScan = true, toVersion = latestVersion)).values.map { it.key })
                    }
                }
                assertFailsWith<StorageException> {
                    store.execute(DeletedInclusiveModel.scan(where = filters.first(), filterSoftDeleted = false))
                }
                assertFailsWith<RequestException> {
                    store.execute(DeletedInclusiveModel.scan(order = DeletedInclusiveModel.ref { group }.ascending(),
                        filterSoftDeleted = false, allowTableScan = true))
                }
            } finally { store.close() }
        }
    }
}

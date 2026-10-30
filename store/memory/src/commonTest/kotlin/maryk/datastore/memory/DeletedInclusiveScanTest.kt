package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.filters.Equals
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.ScanCursor
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private object DeletedInclusiveModel : RootDataModel<DeletedInclusiveModel>(indexes = { listOf(DeletedInclusiveModel.group.ref()) }) {
    val email by string(1u, unique = true)
    val group by string(2u)
}

class DeletedInclusiveScanTest {
    @Test
    fun tableScanIncludesEveryDeletedDuplicateAndPreservesCursorOrder() = runTest {
        for (keepHistory in listOf(false, true)) {
            val store = InMemoryDataStore.open(keepAllVersions = keepHistory, dataModelsById = mapOf(1u to DeletedInclusiveModel))
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

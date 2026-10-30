package maryk.datastore.rocksdb

import maryk.createTestDBFolder
import maryk.deleteFolder
import kotlinx.coroutines.test.runTest
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.pairs.with
import maryk.core.query.requests.get
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReplicationNoopVersionTest {
    @Test
    fun newerNoopRejectsOlderChangesAndDeletesAfterReopen() = runTest {
        for (history in listOf(false, true)) {
            val folder = createTestDBFolder("replication-noop-watermark-$history")
            suspend fun open() = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to SimpleMarykModel), keepAllVersions = history)
            var store = open()
            try {
                val values = SimpleMarykModel.create { value with "haha-A" }
                val key = SimpleMarykModel.key(values)
                val addition = store.processUpdate(UpdateResponse(SimpleMarykModel, AdditionUpdate(key, 10uL, 10uL, 0, false, values)))
                assertIs<AddSuccess<SimpleMarykModel>>(assertIs<AddResponse<SimpleMarykModel>>(addition.result).statuses.single())
                store.processUpdate(UpdateResponse(SimpleMarykModel, ChangeUpdate(key, 30uL, 0,
                    listOf(Change(SimpleMarykModel.ref { value } with "haha-A")))))
                assertEquals(30uL, store.execute(SimpleMarykModel.get(key)).values.single().lastVersion)
                store.processUpdate(UpdateResponse(SimpleMarykModel, ChangeUpdate(key, 40uL, 0,
                    listOf(Check(SimpleMarykModel.ref { value } with "haha-A")))))
                assertEquals(40uL, store.execute(SimpleMarykModel.get(key)).values.single().lastVersion)
                store.close()
                store = open()
                store.processUpdate(UpdateResponse(SimpleMarykModel, ChangeUpdate(key, 20uL, 0,
                    listOf(Change(SimpleMarykModel.ref { value } with "haha-B")))))
                store.processUpdate(UpdateResponse(SimpleMarykModel, RemovalUpdate(key, 20uL, HardDelete)))
                val current = store.execute(SimpleMarykModel.get(key)).values.single()
                assertEquals("haha-A", current.values { value })
                assertEquals(40uL, current.lastVersion)
            } finally {
                store.close()
                deleteFolder(folder)
            }
        }
    }
}

package maryk.datastore.rocksdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import maryk.core.clock.HLC
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InitialChangesNotificationTest {
    @Test
    fun initialCreationNotifiesExistingFlow() = runTest {
        withContext(Dispatchers.Default) {
            val folder = createTestDBFolder("initial-changes-notification")
            val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to SimpleMarykModel))
            try {
                val updates = store.executeFlow(SimpleMarykModel.scan(allowTableScan = true)).produceIn(this)
                try {
                    assertIs<InitialValuesUpdate<*>>(withTimeout(5_000) { updates.receive() })
                    val key = Key<SimpleMarykModel>(ByteArray(16) { 13 })
                    val version = HLC().timestamp
                    store.processUpdate(UpdateResponse(SimpleMarykModel, InitialChangesUpdate(
                        version,
                        listOf(DataObjectVersionedChange(key, changes = listOf(VersionedChanges(
                            version,
                            listOf(ObjectCreate, Change(SimpleMarykModel.ref { value } with "happy replicated")),
                        )))),
                    )))
                    assertEquals("happy replicated", store.execute(SimpleMarykModel.get(key)).values.single().values { value })
                    val addition = assertIs<AdditionUpdate<SimpleMarykModel>>(withTimeout(5_000) { updates.receive() })
                    assertEquals(key, addition.key)
                    assertEquals(version, addition.version)
                    assertEquals("happy replicated", addition.values { value })
                } finally {
                    updates.cancel()
                }
            } finally {
                store.close()
                deleteFolder(folder)
            }
        }
    }
}

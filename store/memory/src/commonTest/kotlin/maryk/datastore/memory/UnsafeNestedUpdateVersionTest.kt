package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.properties.types.Key
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UnsafeNestedUpdateVersionTest {
    @Test
    fun rejectsWholeBatchBeforeApplyingSafePrefix() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        try {
            for (unsafeVersion in listOf(ULong.MAX_VALUE - 1uL, ULong.MAX_VALUE)) {
                val changes = listOf(1uL, unsafeVersion).mapIndexed { index, version ->
                    DataObjectVersionedChange<SimpleMarykModel>(
                        key = Key(ByteArray(16) { index.toByte() }),
                        changes = listOf(VersionedChanges(version, listOf(
                            ObjectCreate,
                            Change(SimpleMarykModel.ref { value } with "haha-replicated"),
                        ))),
                    )
                }
                assertFailsWith<RequestException> {
                    store.processUpdate(UpdateResponse(SimpleMarykModel, InitialChangesUpdate(1uL, changes)))
                }
                assertTrue(store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
            }
            assertIs<AddSuccess<*>>(store.execute(SimpleMarykModel.add(
                SimpleMarykModel.create { value with "haha-after-rejection" }
            )).statuses.single())
        } finally {
            store.close()
        }
    }
}
